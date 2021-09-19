import deformablemesh.io.MeshWriter;
import deformablemesh.track.Track;
import lightgraph.DataSet;
import lightgraph.Graph;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public static void main(String[] args){
        PlotCellCountOverTime pcct = new PlotCellCountOverTime();
        for(int i = 0; i<args.length; i++){
            try {
                pcct.addData(args[i], MeshWriter.loadMeshes(new File(args[i])));
            } catch (IOException e) {
                System.out.println("skipping: " + args[i] + " failed to load.");
                System.out.println(e.getMessage());
            }
        }
        pcct.plotData();

    }
}
