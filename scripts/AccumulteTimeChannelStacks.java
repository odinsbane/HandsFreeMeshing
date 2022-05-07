import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AccumulteTimeChannelStacks {
    static class ChannelTimeStack{
        Path path;
        int c, t;
        int tile_no;
        Pattern p = Pattern.compile("tile_(\\d+).+tp_(\\d+)_ch_(\\d+)");
        public ChannelTimeStack(String loc){
            Matcher m = p.matcher(loc);
            if(m.find()){
                c = Integer.parseInt(m.group(3));
                t = Integer.parseInt(m.group(2));
                tile_no = Integer.parseInt(m.group(1));
            } else{
                c = -1;
                t = -1;
                tile_no = -1;
            }
            path = Paths.get(loc);
        }
        int getTime(){
            return t;
        }
        String getAbsolutePath(){
            return path.toAbsolutePath().toString();
        }

    }
    public static void main(String[] args){
        List<ChannelTimeStack> files = Arrays.stream(args).map(ChannelTimeStack::new).collect(Collectors.toList());
        int supplied = files.size();
        files.removeIf(f -> f.c < 0);
        System.out.println("total: " + supplied + " remaining:" + files.size());
        Map<Integer, List<ChannelTimeStack>> tiles = files.stream().collect(Collectors.groupingBy(t->t.tile_no));
        System.out.println("working on: " + tiles.size() + " tiles");
        for(Integer tile: tiles.keySet()){

            List<ChannelTimeStack> xyzct = tiles.get(tile);
            Map<Integer, List<ChannelTimeStack>> times = xyzct.stream().collect(
                    Collectors.groupingBy(ChannelTimeStack::getTime)
            );
            List<Integer> keys = new ArrayList<>(times.keySet());
            keys.sort(Integer::compareTo);
            ImageStack out = null;
            ImagePlus plus = null;
            int nChannels = -1;
            for(Integer key: keys){

                List<ImageStack> stacks = new ArrayList<>();
                List<ChannelTimeStack> channels = times.get(key);
                channels.sort(Comparator.comparingInt(cst -> cst.c));

                for(ChannelTimeStack cstack: channels){
                    ImagePlus cplus = new ImagePlus(cstack.getAbsolutePath());
                    System.out.println( cstack.tile_no + "\t" + key  + "\t" + cplus.getNSlices() );
                    if(out == null){
                        out = new ImageStack(cplus.getWidth(), cplus.getHeight());
                        plus = cplus;
                        nChannels = channels.size();
                    }
                    if(cplus.getNSlices() != plus.getNSlices() ){
                        System.out.println(
                            "tile: " + cstack.tile_no + 
                            "channels do not contain the same number of slices:" + cplus.getNSlices() + " vs " + plus.getNSlices() +
                            "frame" + key
                            );
                    }
                    stacks.add(cplus.getImageStack());
                }

                for(int z = 0; z < plus.getNSlices(); z++){
                    for(int i = 0; i<channels.size(); i++){
                        ImageStack stack = stacks.get(i);
                        if(stack.size() < z + 1 ){
                            out.addSlice( stack.getProcessor( stack.size() ) );
                        } else{
                            out.addSlice(stacks.get(i).getProcessor(z + 1));
                        }
                        
                    }
                }
            }
            //new ImageJ();
            ImagePlus finished = plus.createImagePlus();
            finished.setStack(out, nChannels, plus.getNSlices(), times.size());
            //finished.show();
            IJ.save(finished, "./stacked/tile_" + tile + ".tif");
        }

    }
}
