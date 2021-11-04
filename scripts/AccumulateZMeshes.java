import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.io.MeshWriter;
import deformablemesh.track.Track;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AccumulateZMeshes {
    
    static Pattern pat = Pattern.compile("\\d+");
    static int getFrame(String s){
        Matcher m = pat.matcher(s);
        String token = "-1";
        while(m.find()){
            //get the last one.
            token = m.group(0);
        }
        return Integer.parseInt(token);
    }

    public static boolean linked(String nameA,  String nameB){
        String a = nameA;
        String b = nameB;
        String[] ak = nameA.split("-");
        if(ak.length > 1){
            a = ak[1];
        }
        String[] bk = nameB.split("-");
        if(bk.length > 1){
            b = bk[1];
        }

        return  a.equals(b);
    }

    public static void main(String[] args) throws ExecutionException, InterruptedException, IOException {
        Runtime runtime = Runtime.getRuntime();
        int procs = runtime.availableProcessors();
        long mem = runtime.maxMemory();
        System.out.println(procs + " cores " + mem + " max mem.");
        List<String> names = new ArrayList<>( );
        Path out;
        if( args.length > 0){
            names.addAll( Arrays.asList(args) );
            Paths.get("");
        } else{
            String dir = IJ.getDirectory("Select folder containing images");

            try {
                names.addAll( Files.list(Paths.get(dir)).filter(p->p.toString().endsWith(".bmf")).map(Path::toString).collect( Collectors.toList() ));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        System.out.println("working on " + names.size() + " files");
        names.sort(Comparator.comparingInt(AccumulateZMeshes::getFrame));
        int frames = 0;
        List<Track> results = new ArrayList<>();
        List<Track> lastFrame = new ArrayList<>();
        for(int j = 0; j<names.size(); j++) {
            final int frame = j;
            List<Track> input = MeshWriter.loadMeshes(
                    new File(names.get(j))
            ).stream().filter(
                    loadedTrack -> loadedTrack.containsKey(0)
            ).collect(Collectors.toList());

            List<Track> appended = input.stream().map(
                    track -> {
                        Track target = lastFrame.stream().filter(
                                oldTrack -> linked(track.getName(), oldTrack.getName())
                        ).findFirst().orElseGet(() ->{
                            Track fresh = new Track(track.getName(), track.getColor());
                            results.add(fresh);
                            return fresh;
                        } );
                        target.addMesh(frame, track.getMesh(0));

                        return target;
                    }
            ).collect(Collectors.toList());

            lastFrame.clear();
            lastFrame.addAll(input);

        }
        System.out.println(results.size());
        if(results.size() > 0)
        MeshWriter.saveMeshes(new File("first-attempt.bmf"), results);
    }

}
