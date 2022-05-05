import deformablemesh.DeformableMesh3DTools;
import deformablemesh.MeshImageStack;
import deformablemesh.geometry.CurvatureCalculator;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.io.MeshReader;
import deformablemesh.track.Track;
import deformablemesh.util.Vector3DOps;
import ij.ImagePlus;
import lightgraph.Graph;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SingleTrackPlotting {
    /**
     * Class for plotting time courses of individiual tracks.
     */

    Graph cmX, cmY, cmZ;
    Graph volume;
    List<Graph> meanIntensity = new ArrayList<>();
    Graph displacements;
    Graph curvatures;
    MeshImageStack working;
    int channels;
    Path log = Paths.get("data-log.txt");
    SingleTrackPlotting(){
        cmX = new Graph();
        cmY = new Graph();
        cmZ = new Graph();
        volume = new Graph();
        displacements = new Graph();
        curvatures = new Graph();
    }

    public void addTrack(Track t, String title){
        List<Integer> tp = new ArrayList<>(t.getTrack().keySet());
        double[] time = new double[tp.size()];
        double[] x = new double[tp.size()];
        double[] z = new double[tp.size()];
        double[] y = new double[tp.size()];
        double[] v = new double[tp.size()];
        double[] kappa = new double[tp.size()];
        double[] kappa2 = new double[tp.size()];
        List<double[]> aveI = new ArrayList<>();
        for(int i = 0; i<channels; i++){
            aveI.add(new double[tp.size()]);
        }
        double dim = working.SCALE;
        double[] origin = new double[3];
        double v0 = 1;

        List<double[]> deltas = new ArrayList<>();
        double lastVolume = -1;
        double[] lastCm = null;
        for(int i = 0; i<tp.size(); i++){
            Integer key = tp.get(i);
            working.setFrame(key);
            time[i] = key;
            DeformableMesh3D mesh = t.getMesh(key);

            double vi = mesh.calculateVolume()*dim*dim*dim;
            double[] cm = DeformableMesh3DTools.centerAndRadius(mesh.nodes);
            if(i == 0 ){
                origin[0] = cm[0];
                origin[1] = cm[1];
                origin[2] = cm[2];
                v0 = vi;
            } else{
                double r = Vector3DOps.mag(Vector3DOps.difference(cm, lastCm));
                double dv = (vi - lastVolume);
                deltas.add(new double[]{r, dv});
            }

            CurvatureCalculator calc = new CurvatureCalculator(mesh);
            List<double[]> curvs = calc.calculateCurvature();

            double kapi = 0;
            double kapi2 = 0;
            double r0 = Math.cbrt( 3 * vi / 4.0 / Math.PI);
            double totalArea = 0;
            for(double[] curv: curvs){
                kapi += curv[3] * curv[7];
                kapi2 += curv[3]*curv[3] * curv[7];
                totalArea += curv[7];

            }
            kapi = kapi/totalArea;
            kapi2 = Math.sqrt( kapi2 / totalArea - kapi*kapi);
            kappa[i] = kapi * r0/dim;
            kappa2[i] = kapi2 * r0/dim;



            v[i] = vi;
            x[i] = (cm[0] - origin[0])*dim;
            y[i] = (cm[1] - origin[1])*dim;
            z[i] = (cm[2] - origin[2])*dim;
            for(int c = 0; c<channels; c++){
                working.setChannel(c);
                aveI.get(c)[i] = DeformableMesh3DTools.getVolumeAverageIntensity(working, mesh);
            }
            lastVolume = vi;
            lastCm = cm;


        }
        double[] deltaV = new double[deltas.size()];
        double[] deltaCm = new double[deltas.size()];
        for(int i = 0; i<deltas.size(); i++){
            deltaV[i] = deltas.get(i)[0];
            deltaCm[i] = deltas.get(i)[1];
        }
        double[] a0 = {0.0};
        double[] b0 = {0.0};
        for(int i = 0; i<deltaV.length; i++){
            a0[0] += deltaV[i];
            b0[0] += deltaCm[i];
        }
        a0[0] = a0[0]/deltaV.length;
        b0[0] = b0[0]/deltaV.length;
        displacements.addData(a0, b0).setLabel(title);


        cmX.addData(time, x).setLabel(title);
        cmY.addData(time, y).setLabel(title);
        cmZ.addData(time, z).setLabel(title);
        volume.addData(time, v).setLabel(title);
        for(int c = 0; c<channels; c++){
            meanIntensity.get(c).addData(time, aveI.get(c)).setLabel(title);
        }

        double kbar = 0;
        double k2bar = 0;
        for(int i = 0; i < kappa.length; i++){
            kbar += kappa[i];
            k2bar += kappa2[i];
        }
        curvatures.addData(new double[]{kbar/kappa.length}, new double[]{k2bar/kappa.length}).setLabel(title);

    }
    public void setImage(ImagePlus plus){
        //channels = plus.getNChannels();
        while(meanIntensity.size() < channels){
            meanIntensity.add(new Graph());
        }
        working = new MeshImageStack(plus);
    }
    public void finish(){
        cmX.show(false, "X center of mass");
        cmY.show(false, "Y center of mass");
        cmZ.show(false, "Z center of mass");
        volume.show(false, "Volume");
        for(int c = 0; c<meanIntensity.size(); c++){
            meanIntensity.get(c).show(false, "intensity: " + c);
        }
        displacements.show(false, "Displacements and Volume");
        curvatures.show(false, "Curvature vs STD [Curvature]");
    }

    public static void main(String[] args) throws IOException {
        Path folder = Paths.get(args[0]);
        List<Path> paths = Files.list(folder).filter(
                p -> p.getFileName().toString().endsWith(".bmf")
        ).collect(Collectors.toList());
        SingleTrackPlotting stp = new SingleTrackPlotting();
        for(Path p: paths){
            Path imgPath = folder.resolve(p.getFileName().toString().replace(".bmf", ".tif"));
            if(Files.exists(imgPath)){
                System.out.println(p + " // " + imgPath);
                ImagePlus plus = new ImagePlus(imgPath.toAbsolutePath().toString());
                List<Track> meshes = MeshReader.loadMeshes(p.toFile());
                stp.setImage(plus);
                String shortName = imgPath.getFileName().toString().replace(".tif", "");
                for(Track t: meshes){
                    stp.addTrack(t, shortName);
                }
            }
        }
        stp.finish();

    }

}
