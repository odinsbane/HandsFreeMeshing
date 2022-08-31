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
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
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
    Color conditionColor = Color.BLACK;
    String conditionName;
    SingleTrackPlotting(){
        cmX = new Graph();
        cmY = new Graph();
        cmZ = new Graph();
        volume = new Graph();
        volumeChanges = new Graph();
        displacements = new Graph();
        curvatures = new Graph();
    }
    public void setConditionColor(Color c){
        conditionColor = c;
    }
    public void setConditionName(String n){
        conditionName = n;
    }

    public void followCenterOfMass( Track t, String title){
        List<Integer> tp = new ArrayList<>(t.getTrack().keySet());

        double[] time = new double[tp.size()];
        double[] x = new double[tp.size()];
        double[] z = new double[tp.size()];
        double[] y = new double[tp.size()];
        double dim = working.SCALE;
        double[] origin = new double[3];

        List<double[]> deltas = new ArrayList<>();
        double lastVolume = -1;
        double[] lastCm = null;
        for(int i = 0; i<tp.size(); i++){
            Integer key = tp.get(i);
            working.setFrame(key);
            time[i] = key;
            DeformableMesh3D mesh = t.getMesh(key);
            double[] cm = DeformableMesh3DTools.centerAndRadius(mesh.nodes);
            if(i == 0 ){
                origin[0] = cm[0];
                origin[1] = cm[1];
                origin[2] = cm[2];
            }
            x[i] = (cm[0] - origin[0])*dim;
            y[i] = (cm[1] - origin[1])*dim;
            z[i] = (cm[2] - origin[2])*dim;
        }
        cmX.addData(time, x).setLabel(title);
        cmY.addData(time, y).setLabel(title);
        cmZ.addData(time, z).setLabel(title);


    }

    public void addCurvatureData(Track t, String title){

    }

    public void addIntensityData(Track t, String title){
        List<Integer> tp = new ArrayList<>(t.getTrack().keySet());
        List<double[]> aveI = new ArrayList<>();
        for(int i = 0; i<channels; i++){
            aveI.add(new double[tp.size()]);
        }

    }

    public void addVolumeData(Track t, String title){
        List<Integer> tp = new ArrayList<>(t.getTrack().keySet());
        double[] time = new double[tp.size()];
        double[] v = new double[tp.size()];
        double lastVolume = -1;
        double dim = working.SCALE;
        for(int i = 0; i<tp.size(); i++){
            Integer key = tp.get(i);
            working.setFrame(key);
            time[i] = key;
            DeformableMesh3D mesh = t.getMesh(key);

            double vi = mesh.calculateVolume()*dim*dim*dim;

            v[i] = vi;
        }

        DataSet set = volume.addData(time, v);
        set.setLabel(title);
        set.setColor(conditionColor);

        double dt = time[time.length - 1] - time[0];
        double[] v0 = { v[0] };
        double[] vf = { ( v[ v.length - 1] - v[0] ) / v[0] / dt };

        set = volumeChanges.addData(v0, vf);
        set.setColor(conditionColor);
        set.setLine(null);
        set.setPoints(GraphPoints.crossPlus());
        set.setPointSize(12);

    }

    public void addTrack(Track t, String title){
        Pattern p = Pattern.compile("_(\\d+)");
        Matcher m = p.matcher(title);
        if(!m.find()){
            System.out.println("cannot find tile number! " + title);
        }
        int tile_number = Integer.parseInt(m.group(1));

        addVolumeData(t, title);
    }

    public void setImage(ImagePlus plus){
        //channels = plus.getNChannels();
        while(meanIntensity.size() < channels){
            meanIntensity.add(new Graph());
        }
        working = new MeshImageStack(plus);
    }
    public void finish(){
        String con = conditionName.replace(".txt", "");
        //cmX.show(false, "X center of mass");
        //cmY.show(false, "Y center of mass");
        //cmZ.show(false, "Z center of mass");
        volume.setXLabel("Frame");
        volume.setYLabel("Volume ( um^3 ) ");
        volume.setTitle("Volume vs Time: " + conditionName.replace(".txt", ""));
        volume.setYRange(0, 100000);
        volume.show(false, "Volume vs Time " + conditionName);
        volume.saveSvg(new File(con + "-tc.svg"));

        volumeChanges.setXLabel("Initial Volume ( um^3 )");
        volumeChanges.setYLabel("(Vf - Vi)/Vi/dt ( um^3/Frame ) ");
        volumeChanges.setTitle("Volume vs Time: " + conditionName.replace(".txt", ""));
        volumeChanges.setYRange(-0.5, 0.5);
        //volumeChanges.setXRange(0, 30000);
        volumeChanges.show(false, "Relative Volume Changes " + conditionName);

        volumeChanges.saveSvg(new File(con + "-rel.svg"));

        for(int c = 0; c<meanIntensity.size(); c++){
            meanIntensity.get(c).show(false, "intensity: " + c);
        }


        //displacements.show(false, "Displacements and Volume");
        //curvatures.show(false, "Curvature vs STD [Curvature]");
    }

    public static void main(String[] args) throws IOException {
        Color[] colors = {
                Color.MAGENTA,
                Color.RED,
                new Color(0, 60, 0),
                Color.ORANGE
        };
        int lastColor = 0;
        for(String condition: args){
            try(BufferedReader reader = Files.newBufferedReader(Paths.get(condition));){
                List<Path> images = reader.lines().filter(
                        line-> ! line.startsWith("#")
                    ).map(
                        line ->{
                            String img = line.split("\\s")[0];
                            return Paths.get(img).toAbsolutePath();
                        }
                    ).collect(Collectors.toList());
                List<Path> paths = images.stream().map(img ->{
                    Path folder = img.getParent();
                    String name = img.getFileName().toString().replace(".tif", ".bmf");
                    return folder.resolve(name);
                }).collect(Collectors.toList());
                SingleTrackPlotting stp = new SingleTrackPlotting();
                stp.setConditionColor(colors[lastColor]);
                stp.setConditionName(condition);
                lastColor = (lastColor + 1)%colors.length;
                for(int i = 0; i<paths.size(); i++){
                    Path imgPath = images.get(i);
                    Path meshPath = paths.get(i);
                    if(Files.exists(imgPath) && Files.exists(meshPath)){
                        System.out.println(meshPath + " // " + imgPath);
                        ImagePlus plus = FileInfoVirtualStack.openVirtual(imgPath.toAbsolutePath().toString());
                        List<Track> meshes = MeshReader.loadMeshes(meshPath.toFile());

                        meshes.removeIf(t -> t.size() < 2);

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



    }

}
