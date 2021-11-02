import Jama.LUDecomposition;
import Jama.Matrix;
import deformablemesh.DeformableMesh3DTools;
import deformablemesh.MeshImageStack;
import deformablemesh.SegmentationController;
import deformablemesh.SegmentationModel;
import deformablemesh.geometry.BinaryMomentsOfInertia;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.gui.render2d.RenderFrame2D;
import deformablemesh.io.MeshWriter;
import deformablemesh.track.Track;
import deformablemesh.util.Vector3DOps;
import ij.ImagePlus;
import lightgraph.DataSet;
import lightgraph.Graph;
import lightgraph.GraphPoints;

import java.io.File;
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
    public void addData(String name, List<Track> tracks){
        while(trackData.keySet().contains(name)){
            name = name + "I";
        }
        trackData.put(name, tracks);
        for(Track t: tracks){
            if(t.getFirstFrame()<firstFrame){
                firstFrame = t.getFirstFrame();
            }
            if(t.getLastFrame()>lastFrame){
                lastFrame = t.getLastFrame();
            }
        }
    }

    /**
     *
     * @return first frame inclussive.
     */
    int getFirstFrame(){

        return -1;
    }

    /**
     *
     * @return last frame inclussive.
     */
    int getLastFrame(){
        return -1;
    }

    int getNFrames(){
        return lastFrame - firstFrame + 1;
    }
    public void plotData(){
        Graph plot = new Graph();
        for(Map.Entry<String, List<Track>> entries: trackData.entrySet()) {
            List<Track> tracks = entries.getValue();

            double[] ret = new double[getNFrames()];
            double[] frame = new double[getNFrames()];

            for (int i = 0; i < getNFrames(); i++) {
                frame[i] = i;
                int s = 0;
                for (Track t : tracks) {
                    if (t.containsKey(i)) {
                        s++;
                    }
                }

                ret[i] = s;
            }
            DataSet set = plot.addData(frame, ret);
            set.setLabel(entries.getKey());
        }
        plot.show(false, "Number of cells vs time");


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
            List<Track> tracks = MeshWriter.loadMeshes(meshes.get(i).toFile());
            pccot.plotAngularMomentumVsTime(tracks, new MeshImageStack(plus));

        }
        pccot.finish();
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




    Graph averageAngularMomentum = new Graph();
    Graph centerOfMass = new Graph();
    int processed = 0;
    String working;

    public void plotDisplacementsVsTime(List<Track> tracks, MeshImageStack meshImageStack) {
        averageAngularMomentum = new Graph();
        //averageAngularMomentum.setYRange(0, 500);
        averageAngularMomentum.setTitle(working + "angular velocity");

        Graph displacementPlot = new Graph();
        displacementPlot.setTitle(working + "Values over Time");

        displacementPlot.setXRange(0, 135);
        displacementPlot.setXTicCount(6);
        //displacementPlot.setYRange(0, 10);
        displacementPlot.setYTicCount(6);
        displacementPlot.setXLabel("Frame No.");
        String unit = meshImageStack.getUnits();
        if(unit == null){
            unit = "au";
        }
        displacementPlot.setYLabel("Angular Velocity ( " + unit + "^2 )");
        displacementPlot.setTitle("Angular Velocity vs Time");


        double[] frames = new double[ meshImageStack.getNFrames() ];
        double[] totalTorque = new double[meshImageStack.getNFrames()];
        double[] averageTorqueMagnitude = new double[meshImageStack.getNFrames()];
        double[] comDelta = new double[frames.length];
        double[] netDisplacement = new double[frames.length];

        for(int i = 0; i<meshImageStack.getNFrames(); i++){
            frames[i] = i;

            //center at two different times.
            double[] center0 = new double[3];
            double[] center1 = new double[3];
            //volume at two different times.
            double volume0 = 0.0;
            double volume1 = 0.0;

            for(Track t: tracks){

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

            double[] moment = new double[3];
            double mm = 0;
            double n = 0;

            double[] delta = new double[3];
            double[][] inertialMatrix = new double[3][3];

            for(Track t: tracks){
                if(t.size()<2){
                    continue;
                }



                DataSet d2 = null;
                if(t.containsKey(i-1) && t.containsKey(i+1)){
                    DeformableMesh3D start = t.getMesh(i-1);
                    DeformableMesh3D fin = t.getMesh(i+1);
                    double[] cs = DeformableMesh3DTools.centerAndRadius(start.nodes);
                    double[] cf = DeformableMesh3DTools.centerAndRadius(fin.nodes);
                    if(d2 == null){
                        d2 = displacementPlot.addData(new double[0], new double[0]);
                        d2.setLabel(null);
                        d2.setColor(t.getColor());
                        if(t.getFirstFrame() == 0 && t.getLastFrame() == meshImageStack.getNFrames()-1){
                            d2.setPoints(GraphPoints.filledTriangles());
                        }
                    }
                    double[] csp = Vector3DOps.difference(cs, center0);
                    double[] cfp = Vector3DOps.difference(cf, center1);

                    double[] v = Vector3DOps.difference(cfp, csp);

                    double d =  Vector3DOps.mag( Vector3DOps.difference(cfp, csp) ) * meshImageStack.SCALE;

                    double[] r = Vector3DOps.average(csp, cfp);

                    double v1 = fin.calculateVolume();
                    double v2 = start.calculateVolume();
                    double factor = 0.5*(v1+v2)*meshImageStack.SCALE*meshImageStack.SCALE*meshImageStack.SCALE;
                    for(int k = 0; k<3; k++){
                        int a = k;
                        int b = (k+1)%3;
                        int c = (k+2)%3;

                        inertialMatrix[a][a] += ( r[b]*r[b] + r[c]*r[c] ) * factor;
                        inertialMatrix[b][a] += -r[b]*r[a]*factor;
                        inertialMatrix[c][a] += -r[c]*r[a]*factor;
                    }

                    double[] angMom = Vector3DOps.cross(r, v);
                    moment[0] += angMom[0]*meshImageStack.SCALE*meshImageStack.SCALE;
                    moment[1] += angMom[1]*meshImageStack.SCALE*meshImageStack.SCALE;
                    moment[2] += angMom[2]*meshImageStack.SCALE*meshImageStack.SCALE;
                    n++;

                    double tq = Vector3DOps.mag(angMom)*meshImageStack.SCALE*meshImageStack.SCALE;
                    mm += tq;

                    d2.addPoint(i + 0.5, d);
                    delta[0] += v[0];
                    delta[1] += v[1];
                    delta[2] += v[2];
                }

            }

            Matrix iM = new Matrix(inertialMatrix);
            LUDecomposition lu = new LUDecomposition(iM);
            try {
                Matrix s = lu.solve(new Matrix(moment, 3));

                delta[0] = delta[0] / n * meshImageStack.SCALE;
                delta[1] = delta[1] / n * meshImageStack.SCALE;
                delta[2] = delta[2] / n * meshImageStack.SCALE;

                //netDisplacement[i] = Vector3DOps.mag(Vector3DOps.average(center0, center1))*meshImageStack.SCALE;
                netDisplacement[i] = Vector3DOps.average(center0, center1)[0] * meshImageStack.SCALE;

                double [] angularVelocity = s.getRowPackedCopy();
                totalTorque[i] = Vector3DOps.normalize(angularVelocity);

                averageTorqueMagnitude[i] = Vector3DOps.mag(moment);
            }catch(Exception e){
                    //singular matrix? skip it.
            }
        }
        centerOfMass.addData(frames, netDisplacement).setLabel(working);
        displacementPlot.addData(frames, comDelta).setLabel("center of mass displacement");
        displacementPlot.addData(frames, netDisplacement).setLabel("center of mass distance from origin");
        displacementPlot.show(false, "Displacement per Frame " + working);


        DataSet s1 = averageAngularMomentum.addData(frames, totalTorque);
        s1.setLabel("Magnitude of average angular momentum: " + processed);

        DataSet s2 = averageAngularMomentum.addData(frames, averageTorqueMagnitude);
        s2.setLabel("Magnitude of angular velocity magnitude: " + processed);

        averageAngularMomentum.show(true, "angular momentum");
        processed++;
    }


    public void plotAngularMomentumVsTime(List<Track> tracks, MeshImageStack meshImageStack) {
        averageAngularMomentum = new Graph();
        averageAngularMomentum.setTitle(working + "angular velocity");

        String unit = meshImageStack.getUnits();
        if(unit == null){
            unit = "au";
        }

        int N = meshImageStack.getNFrames();

        double[] frames = new double[ N ];
        double[] totalTorque = new double[ N ];
        double[] averageTorqueMagnitude = new double[ N ];

        for(int i = 0; i<meshImageStack.getNFrames(); i++){
            int frame = i;
            frames[i] = i;

            //center at two different times.
            double[] center0 = new double[3];
            double[] center1 = new double[3];
            //volume at two different times.
            double volume0 = 0.0;
            double volume1 = 0.0;
            List<Track> working = tracks.stream().filter(
                    t->t.containsKey(frame-1) && t.containsKey(frame+1)
            ).collect(Collectors.toList());

            for(Track t: working){
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

            //weighted center of mass for all of the objects.
            center0[0] = center0[0]/volume0;
            center0[1] = center0[1]/volume0;
            center0[2] = center0[2]/volume0;

            center1[0] = center1[0]/volume1;
            center1[1] = center1[1]/volume1;
            center1[2] = center1[2]/volume1;

            double mm = 0;
            double n = 0;

            double[][] inertialMatrix = new double[3][3];
            double[] angularMomentum = new double[3];

            for(Track t: working){

                DeformableMesh3D start = t.getMesh(frame-1);
                DeformableMesh3D fin = t.getMesh(frame+1);

                double[] cs = DeformableMesh3DTools.centerAndRadius(start.nodes);
                double[] cf = DeformableMesh3DTools.centerAndRadius(fin.nodes);

                double[] csp = Vector3DOps.difference(cs, center0);
                double[] cfp = Vector3DOps.difference(cf, center1);

                double[] v = Vector3DOps.difference(cfp, csp);

                double[] r = Vector3DOps.average(csp, cfp);

                double v1 = fin.calculateVolume();
                double v2 = start.calculateVolume();

                double factor = 0.5*(v1+v2)*meshImageStack.SCALE*meshImageStack.SCALE*meshImageStack.SCALE;
                for(int k = 0; k<3; k++){
                    int a = k;
                    int b = (k+1)%3;
                    int c = (k+2)%3;

                    inertialMatrix[a][a] += ( r[b]*r[b] + r[c]*r[c] ) * factor;
                    inertialMatrix[b][a] += -r[b]*r[a]*factor;
                    inertialMatrix[c][a] += -r[c]*r[a]*factor;
                }

                double[] angMom = Vector3DOps.cross(r, v);
                angularMomentum[0] += angMom[0]*meshImageStack.SCALE*meshImageStack.SCALE;
                angularMomentum[1] += angMom[1]*meshImageStack.SCALE*meshImageStack.SCALE;
                angularMomentum[2] += angMom[2]*meshImageStack.SCALE*meshImageStack.SCALE;
                n++;

                double tq = Vector3DOps.mag(angMom)*meshImageStack.SCALE*meshImageStack.SCALE;
                mm += tq;

            }

            Matrix iM = new Matrix(inertialMatrix);
            LUDecomposition lu = new LUDecomposition(iM);
            try {
                Matrix s = lu.solve(new Matrix(angularMomentum, 3));

                //netDisplacement[i] = Vector3DOps.mag(Vector3DOps.average(center0, center1))*meshImageStack.SCALE;

                double [] angularVelocity = s.getRowPackedCopy();
                totalTorque[i] = Vector3DOps.normalize(angularVelocity);

                averageTorqueMagnitude[i] = Vector3DOps.mag(angularMomentum);
            }catch(Exception e){
                //singular matrix? skip it.
            }
        }
        DataSet s1 = averageAngularMomentum.addData(frames, totalTorque);
        s1.setLabel("Magnitude of average angular momentum: " + processed);

        DataSet s2 = averageAngularMomentum.addData(frames, averageTorqueMagnitude);
        s2.setLabel("Magnitude of angular velocity magnitude: " + processed);

        averageAngularMomentum.show(true, "angular momentum");
        processed++;
    }

    void finish(){
        averageAngularMomentum.show(false);
        centerOfMass.show(false);
    }
}
