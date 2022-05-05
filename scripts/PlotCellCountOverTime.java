import Jama.LUDecomposition;
import Jama.Matrix;
import deformablemesh.DeformableMesh3DTools;
import deformablemesh.MeshImageStack;
import deformablemesh.geometry.BinaryMomentsOfInertia;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.io.MeshReader;
import deformablemesh.track.Track;
import deformablemesh.util.GroupDynamics;
import deformablemesh.util.MeshGroup;
import deformablemesh.util.Vector3DOps;
import ij.ImagePlus;
import lightgraph.DataSet;
import lightgraph.Graph;
import lightgraph.GraphPoints;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * This is going to compile a bunch of plots. First there are file specific plots.
 *  number of cells
 *  angular momentum
 *
 *  Then there are cell specific plots
 *    displacement
 *    volume
 *
 */
public class PlotCellCountOverTime {

    Map<String, List<Track>> trackData =  new HashMap<>();
    int firstFrame = Integer.MAX_VALUE;
    int lastFrame = 0;
    double dtHours = 24.0/133;

    /**
     *
     * @return first frame inclussive.
     */
    int getFirstFrame(){

        return -1;
    }

    int getNFrames(){
        return lastFrame - firstFrame + 1;
    }


    final static Pattern pat = Pattern.compile("\\d+");
    static int getFrame(Path p){
        Matcher m = pat.matcher(p.toString());
        String token = "-1";
        if(m.find()){
            //get the last one.
            token = m.group(0);
        }
        return Integer.parseInt(token);
    }
    String getShortLabel(){
        String full = working;
        Pattern p = Pattern.compile("Tile_\\d");
        Matcher m = p.matcher(full);
        if(m.find()){
            return m.group(0);
        }
        return "Tile_X";
    }
    public static void main(String[] args) throws IOException {
        Path imageFolder = Paths.get(args[0]);
        Path meshFolder = Paths.get(args[1]);
        List<Path> images = Files.list(imageFolder).filter(p->p.getFileName().toString().endsWith(".tif")).collect(Collectors.toList());
        List<Path> meshes = Files.list(meshFolder).filter(m->m.getFileName().toString().endsWith(".bmf")).collect(Collectors.toList());
        images.sort(Comparator.comparingInt(PlotCellCountOverTime::getFrame));
        meshes.sort(Comparator.comparingInt(PlotCellCountOverTime::getFrame));
        if(images.size() != meshes.size()){
            System.err.println("meshes and images are not 1 to 1 " + images.size() + ", " + meshes.size());
            System.out.println("images: " + images);
            System.out.println("meshes: " + meshes);
            return;
        }

        //SegmentationController c = new SegmentationController(new SegmentationModel());
        PlotCellCountOverTime pccot = new PlotCellCountOverTime();

        for(int i = 0; i<images.size(); i++){
            pccot.working = "img: " + images.get(i) + " mesh: " + meshes.get(i);
            System.out.println("working on " + pccot.working);
            ImagePlus plus = new ImagePlus(images.get(i).toString());
            List<Track> tracks = MeshReader.loadMeshes(meshes.get(i).toFile());
            pccot.plotAngularVelocity(tracks, new MeshImageStack(plus));
            pccot.plotDisplacementsVsTime(tracks, new MeshImageStack(plus));
            pccot.finishedSet();
        }
        pccot.finish();
    }

    private void finishedSet() {
        processed++;
    }


    double[][] calculateInertialMatrix( List<DeformableMesh3D> meshes, MeshImageStack mist){

        double[][] inirtialMatrix = new double[3][3];
        double[] c = new double[3];
        double factor = mist.SCALE*mist.SCALE;
        for(DeformableMesh3D mesh: meshes){

            BinaryMomentsOfInertia bmi = new BinaryMomentsOfInertia(mesh, mist);
            double[] r = bmi.getCenterOfMass();
            double[] ii = bmi.getInertialMatrix();
            double v = bmi.volume();

            for(int k = 0; k<3; k++){
                int a = k;
                int b = (k+1)%3;
                int c1 = (k+2)%3;

                inirtialMatrix[a][a] += (r[b]*r[b] + r[c1]*r[c1])*factor*v;
                inirtialMatrix[b][a] += -r[b]*r[a]*factor*v;
                inirtialMatrix[c1][a] += -r[c1]*r[a]*factor*v;
            }


        }

        return inirtialMatrix;


    }




    Graph averageAngularMomentum;
    Graph angularVelocityVsMass;
    Graph displacementHistogram;
    Graph cellsPerFrame;
    int processed = 0;
    String working;
    Graph displacementPlot = new Graph();
    double DT = 11.0; //11 minute frames,
    public void plotDisplacementsVsTime(List<Track> tracks, MeshImageStack meshImageStack) {

        if(displacementPlot == null){
            displacementPlot = new Graph();
            displacementPlot.setXTicCount(6);
            displacementPlot.setYTicCount(6);
            displacementPlot.setXLabel("Volume");
        }

        displacementPlot.setTitle(working + "Values over Time");
        String unit = meshImageStack.getUnits();
        if(unit == null){
            unit = "au";
        }
        displacementPlot.setYLabel("Displacements per Frame ( " + unit + "/s )");
        displacementPlot.setTitle("Mass vs. Displacment");

        double[] frames = new double[ meshImageStack.getNFrames() ];
        double[] comDelta = new double[frames.length];
        double[] netDisplacement = new double[frames.length];
        List<double[]> displacements = new ArrayList<>();
        double[] ncells = new double[frames.length];
        DataSet d2 = null;
        for(int i = 0; i<meshImageStack.getNFrames(); i++){
            frames[i] = i*dtHours;

            //center at two different times.
            double[] center0 = new double[3];
            double[] center1 = new double[3];
            //volume at two different times.
            double volume0 = 0.0;
            double volume1 = 0.0;

            for(Track t: tracks){
                if(t.containsKey(i)){
                    ncells[i] += 1;
                }
                if(t.containsKey(i-1) && t.containsKey(i+1)){
                    DeformableMesh3D start = t.getMesh(i-1);
                    DeformableMesh3D fin = t.getMesh(i+1);

                    double v0 = start.calculateVolume();
                    double[] c = DeformableMesh3DTools.centerAndRadius(start.nodes);
                    center0[0] += c[0]*v0;
                    center0[1] += c[1]*v0;
                    center0[2] += c[2]*v0;
                    volume0 += v0;

                    double v1 = fin.calculateVolume();
                    c = DeformableMesh3DTools.centerAndRadius(fin.nodes);
                    center1[0] += c[0]*v1;
                    center1[1] += c[1]*v1;
                    center1[2] += c[2]*v1;
                    volume1 += v1;

                }
            }
            //weighted center of mass for all of the objects.
            center0[0] = center0[0]/volume0;
            center0[1] = center0[1]/volume0;
            center0[2] = center0[2]/volume0;

            center1[0] = center1[0]/volume1;
            center1[1] = center1[1]/volume1;
            center1[2] = center1[2]/volume1;

            comDelta[i] = Vector3DOps.mag(Vector3DOps.difference(center1, center0))*meshImageStack.SCALE;

            for(Track t: tracks){
                if(t.size()<2){
                    continue;
                }

                if(t.containsKey(i-1) && t.containsKey(i+1)){
                    DeformableMesh3D start = t.getMesh(i-1);
                    DeformableMesh3D fin = t.getMesh(i+1);
                    double volume = 0.5*(start.calculateVolume() + fin.calculateVolume());

                    double[] cs = DeformableMesh3DTools.centerAndRadius(start.nodes);
                    double[] cf = DeformableMesh3DTools.centerAndRadius(fin.nodes);
                    if(d2 == null){
                        d2 = displacementPlot.addData(new double[0], new double[0]);
                        d2.setLine(null);
                        d2.setColor(getProcessedColor());
                        d2.setPoints(GraphPoints.filledCircles());
                        d2.setPointSize(3.0);
                        d2.setLabel(processed + " " + getShortLabel());
                    }

                    double[] csp = Vector3DOps.difference(cs, center0);
                    double[] cfp = Vector3DOps.difference(cf, center1);

                    double[] v = Vector3DOps.difference(cfp, csp);

                    v[0] = v[0]*meshImageStack.SCALE;
                    v[1] = v[1]*meshImageStack.SCALE;
                    v[2] = v[2]*meshImageStack.SCALE;
                    displacements.add(v);
                    double d =  Vector3DOps.mag( Vector3DOps.difference(cfp, csp) ) * meshImageStack.SCALE/DT;



                    //d2.addPoint(0.5*(volume0 + volume1)*Math.pow(meshImageStack.SCALE, 3), d);
                    d2.addPoint( volume * Math.pow(meshImageStack.SCALE, 3),  d); //single cell volume.
                }

            }


            //netDisplacement[i] = Vector3DOps.mag(Vector3DOps.average(center0, center1))*meshImageStack.SCALE/DT;
            netDisplacement[i] = Vector3DOps.average(center0, center1)[0] * meshImageStack.SCALE;
        }
        String shortLabel = getShortLabel();
        DataSet hc = addDisplacementHistogramCurve(displacements);
        hc.setLabel(processed + " " + shortLabel);
        hc.setPoints(null);
        hc.setLineWidth(2);

        if(cellsPerFrame == null){
            cellsPerFrame = new Graph();
        }
        DataSet cpf = cellsPerFrame.addData(frames, ncells);
        cpf.setLabel(shortLabel);
        cpf.setPoints(null);
        cpf.setLineWidth(2);
        cpf.setColor(getProcessedColor());
    }
    public DataSet addDisplacementHistogramCurve(List<double[]> displacements){
        double dtSeconds = dtHours*60*60;
        double dtMinutes = dtHours*60;
        double max = 20/dtMinutes;
        double min = 0;
        int bins = 50;
        double dx = (max - min)/bins;

        double[] counts = new double[bins];
        double[] xs = new double[bins];
        for(int i = 0; i<xs.length; i++){
            xs[i] = (i + 0.5)*dx + min;
        }
        double total = 0;
        double distance_sum = 0.0;
        for(double[] d: displacements){
            double m = Vector3DOps.mag(d)/dtMinutes;
            distance_sum += m;
            int dex = (int)(m/dx);
            if(dex >= 0 && dex < bins){
                counts[dex] += 1;
                total += 1;
            }
        }
        System.out.println("average: " + (distance_sum/total));

        for(int i = 0; i<counts.length; i++){
            counts[i] = counts[i]/total/dx;
        }
        double maxCount = 0;
        for(int i = 0; i<counts.length; i++){
            if(counts[i] > maxCount){
                maxCount = counts[i];
            }
        }
        if(displacementHistogram==null){
            displacementHistogram = new Graph();
        }
        int set = displacements.size();

        Color c = colors[processed % colors.length];

        DataSet mean = displacementHistogram.addData(new double[] { distance_sum/total }, new double[]{ 0.6*maxCount } );
        mean.setColor(c);
        mean.setPointSize(12);
        mean.setPointWeight(3);
        mean.setPoints(GraphPoints.crossX());
        DataSet curve = displacementHistogram.addData(xs, counts);
        curve.setColor(c);
        return curve;
    }

    Color[] colors = {
            Color.BLUE,
            Color.RED,
            Color.GREEN,
            Color.MAGENTA,
            Color.ORANGE,
            Color.BLACK,
            new Color(200, 200, 255),
            new Color(0, 75, 0)
    };

    Color getProcessedColor(){
        return colors[processed%colors.length];
    }

    /**
     *
     * @param tracks
     * @param meshImageStack
     */
    public void plotAngularVelocity(List<Track> tracks, MeshImageStack meshImageStack) {
        if(averageAngularMomentum == null){
            averageAngularMomentum = new Graph();
        }
        averageAngularMomentum.setTitle("angular velocity per time");

        if(angularVelocityVsMass == null){
            angularVelocityVsMass = new Graph();
        }
        String unit = meshImageStack.getUnits();
        if(unit == null){
            unit = "au";
        }

        int N = meshImageStack.getNFrames();

        double[] frames = new double[ N ];
        double[] angularVelocity = new double[ N ];

        List<double[]> angularVelocityPoints = new ArrayList<>();
        for(int i = 0; i<meshImageStack.getNFrames(); i++){
            int frame = i;
            frames[i] = i;



            List<DeformableMesh3D> current = tracks.stream().filter(
                    t->t.containsKey(frame)
            ).map(
                    t -> t.getMesh(frame)
            ).collect(Collectors.toList());

            MeshGroup mg = new MeshGroup(current);
            List<Track> stepping = GroupDynamics.steppingMeshes(tracks, i - 1);

            if(stepping.size() > 0){
                List<DeformableMesh3D> start = GroupDynamics.getMeshInFrame(stepping, i - 1);
                List<DeformableMesh3D> fin = GroupDynamics.getMeshInFrame(stepping, i);
                double[] omega = GroupDynamics.getAxisRotation(start, fin);
                double m = Vector3DOps.normalize(omega);
                angularVelocityPoints.add(new double[]{mg.volume/current.size()*Math.pow(meshImageStack.SCALE, 3), m});
                //angularVelocityPoints.add(new double[]{mg.volume*Math.pow(meshImageStack.SCALE, 3), m});

                angularVelocity[i] = m;
            }

        }

        DataSet s1 = averageAngularMomentum.addData(frames, angularVelocity);
        s1.setLabel(processed + " " + getShortLabel());
        s1.setColor(getProcessedColor());
        double[] x = new double[angularVelocityPoints.size()];
        double[] y = new double[angularVelocityPoints.size()];
        for(int i = 0; i<x.length; i++){
            double[] pt = angularVelocityPoints.get(i);
            x[i] = pt[0];
            y[i] = pt[1];
        }
        DataSet set = angularVelocityVsMass.addData(x, y);
        set.setLine(null);
        set.setLabel(processed + " " + getShortLabel());
        set.setColor(getProcessedColor());

    }

    void finish(){
        if(averageAngularMomentum != null){
            averageAngularMomentum.show(false, "angular momentum");
            averageAngularMomentum.refresh(true);

        }
        if(angularVelocityVsMass != null) {
            angularVelocityVsMass.setXRange(0, 1000);
            angularVelocityVsMass.setYRange(0, 0.4);
            angularVelocityVsMass.show(false, "mass vs angular velocity");
            angularVelocityVsMass.refresh(true);
        }

        if(displacementHistogram != null){
            displacementHistogram.setContentSize(340, 240);
            displacementHistogram.setContentSize(920, 480);
            displacementHistogram.show(false);
            displacementHistogram.setXLabel("Displacement per frame ( um/min )");
            displacementHistogram.refresh(true);
        }
        if(cellsPerFrame != null){
            cellsPerFrame.setContentSize(340, 240);
            cellsPerFrame.setYRange(0, 40);
            cellsPerFrame.setXRange(0, 24);
            cellsPerFrame.setXTicCount(5);
            cellsPerFrame.setYTicCount(5);
            cellsPerFrame.show(false);
            cellsPerFrame.refresh(true);
        }
        if(displacementPlot != null){
            displacementPlot.setXRange(0, 1000);
            displacementPlot.setYRange(0, 1.5);
            displacementPlot.show(false, "displacemnts");
        }


    }
}
