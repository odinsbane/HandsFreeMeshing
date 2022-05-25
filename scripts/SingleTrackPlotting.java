import deformablemesh.DeformableMesh3DTools;
import deformablemesh.MeshImageStack;
import deformablemesh.geometry.CurvatureCalculator;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.io.MeshReader;
import deformablemesh.track.Track;
import deformablemesh.util.Vector3DOps;
import ij.ImagePlus;
import ij.plugin.FileInfoVirtualStack;
import lightgraph.DataSet;
import lightgraph.Graph;
import lightgraph.GraphPoints;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SingleTrackPlotting {
    /**
     * Class for plotting time courses of individiual tracks.
     */

    Graph cmX, cmY, cmZ;
    Graph volume, volumeChanges;
    List<Graph> meanIntensity = new ArrayList<>();
    Graph displacements;
    Graph curvatures;
    MeshImageStack working;
    int channels;
    SingleTrackPlotting(){
        cmX = new Graph();
        cmY = new Graph();
        cmZ = new Graph();
        volume = new Graph();
        volumeChanges = new Graph();
        displacements = new Graph();
        curvatures = new Graph();
    }
    int[][] cutoffs = {
            {0, 14}, //healthy no dye
            {15, 25}, // healthy dye
            {26, 40}, // cancer no dye
            {41, 52}  // cancer dye
    };
    /*
    #0-11 healthy no nuclear dye
#12-23 cancer no nuclear dye
#24 - 36 healthy with nuclear dye
#37 - 49 cancer with nuclear dye
     */
    int[][] cutoffs2 = {
                        {0, 11},
                        {24, 36},
                        {12, 23},
                        {37, 49}
                    };
    Color[] colors = {
            Color.MAGENTA,
            Color.RED,
            new Color(0, 60, 0),
            Color.ORANGE
    };
    public void addTrack(Track t, String title){
        Pattern p = Pattern.compile("_(\\d+)");
        Matcher m = p.matcher(title);
        if(!m.find()){
            System.out.println("cannot find tile number! " + title);
        }
        int tile_number = Integer.parseInt(m.group(1));
        Color tileColor = null;
        int condition = -1;
        for(int i = 0; i<cutoffs.length; i++){
            int[] range = cutoffs2[i];
            if(tile_number >= range[0] && tile_number <= range[1]){
                tileColor = colors[i];
                condition = i;
                break;
            }
        }
        if(condition < 0){
            tileColor = Color.BLACK;
        }
        List<Integer> tp = new ArrayList<>(t.getTrack().keySet());
        double[] time = new double[tp.size()];
        double[] x = new double[tp.size()];
        double[] z = new double[tp.size()];
        double[] y = new double[tp.size()];
        double[] v = new double[tp.size()];
        double[] deltav = new double[tp.size()];
        double[] kappa = new double[tp.size()];
        double[] kappa2 = new double[tp.size()];
        List<double[]> aveI = new ArrayList<>();
        for(int i = 0; i<channels; i++){
            aveI.add(new double[tp.size()]);
        }
        double dim = working.SCALE;
        double[] origin = new double[3];

        List<double[]> deltas = new ArrayList<>();
        double lastVolume = -1;
        double[] lastCm = null;
        for(int i = 0; i<tp.size(); i++){
            Integer key = tp.get(i);
            working.setFrame(key);
            time[i] = key + tile_number/1000.0;
            DeformableMesh3D mesh = t.getMesh(key);

            double vi = mesh.calculateVolume()*dim*dim*dim;
            double[] cm = DeformableMesh3DTools.centerAndRadius(mesh.nodes);
            if(i == 0 ){
                origin[0] = cm[0];
                origin[1] = cm[1];
                origin[2] = cm[2];
            } else{
                double r = Vector3DOps.mag(Vector3DOps.difference(cm, lastCm));
                double dv = (vi - lastVolume);
                deltav[i] = dv / lastVolume;
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
        DataSet set = volume.addData(time, v);
        set.setColor(tileColor);
        set = volumeChanges.addData(time, deltav);
        set.setColor(tileColor);
        set.setLine(null);
        set.setPoints(GraphPoints.crossPlus());
        set.setPointSize(12);
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

        //cmX.show(false, "X center of mass");
        //cmY.show(false, "Y center of mass");
        //cmZ.show(false, "Z center of mass");
        volume.show(false, "Volume");
        volumeChanges.show(false, "Relative Volume Changes");
        for(int c = 0; c<meanIntensity.size(); c++){
            meanIntensity.get(c).show(false, "intensity: " + c);
        }
        //displacements.show(false, "Displacements and Volume");
        //curvatures.show(false, "Curvature vs STD [Curvature]");
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
                ImagePlus plus = FileInfoVirtualStack.openVirtual(imgPath.toAbsolutePath().toString());

                List<Track> meshes = MeshReader.loadMeshes(p.toFile());
                meshes.removeIf(t -> t.size() < 100);
                stp.setImage(plus);
                String shortName = imgPath.getFileName().toString().replace(".tif", "");
                int count = 0;
                for(Track t: meshes){
                    stp.addTrack(t, shortName + "_track-" + count++);
                }
            }
        }
        stp.finish();
    }

}
