import deformablemesh.DeformableMesh3DTools;
import deformablemesh.geometry.BinaryMomentsOfInertia;
import deformablemesh.geometry.Box3D;
import deformablemesh.geometry.DeformableMesh3D;
import deformablemesh.io.MeshReader;
import deformablemesh.io.MeshWriter;
import deformablemesh.track.FrameToFrameDisplacement;
import deformablemesh.track.Track;
import deformablemesh.util.MeshAnalysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class CompareMeshFiles {
    int falsePositive;
    int truePositive;
    int falseNegative;
    int total;
    static class Mapping implements Comparable<Mapping>{
        final DeformableMesh3D a,b;
        final double value;
        public Mapping(DeformableMesh3D a, DeformableMesh3D b, double value){
            this.a = a;
            this.b = b;
            this.value = value;
        }

        @Override
        public int compareTo(Mapping mapping) {
            return Double.compare(value, mapping.value);
        }
        @Override
        public String toString(){
            return a + "->" + b + " :: " + value;
        }
    }

    /**
     * For each mesh in the from, find the best matched mesh in to collection.
     * @param from
     * @param to
     * @return
     */
    void evaluatePrediction(List<DeformableMesh3D> from, List<DeformableMesh3D> to){
        //forward
        List<Mapping> forward = new ArrayList<>();
        List<Mapping> backwards = new ArrayList<>();

        //results.
        List<DeformableMesh3D> falseNegatives = new ArrayList<>();
        List<DeformableMesh3D> falsePositives = new ArrayList<>();
        List<DeformableMesh3D> truePositives = new ArrayList<>();

        for(DeformableMesh3D mesh: from){
            List<Mapping> mapped = getMappings(mesh, to);
            if(mapped.size() > 0){
                forward.add(mapped.get( mapped.size() - 1));
            } else{
                falseNegatives.add(mesh);
            }
        }

        for(DeformableMesh3D mesh: to){
            List<Mapping> mapped = getMappings(mesh, from);
            if(mapped.size() > 0){

                backwards.add(mapped.get( mapped.size() - 1));
            } else{
                falsePositives.add(mesh);
            }
        }

        for(int i = 0; i<forward.size(); i++){
            Mapping m = forward.get(i);
            boolean oneToOne = true;
            for(int j = 0; j<forward.size(); j++){
                if(j==i){ continue;}
                Mapping o = forward.get(j);
                if(m.b == o.b){
                    //multiple
                    oneToOne = false;
                }
            }

            if(!oneToOne){
                //false negative.
                falseNegatives.add(m.a);
                continue;
            }

            for(int j = 0; j<backwards.size(); j++){
                Mapping o = backwards.get(j);
                if(m.a == o.b && m.b != o.a){
                    //false positive. multiple predicted meshes map to 1 original.
                    falsePositives.add(m.b);
                    oneToOne = false;
                }
            }

            if(oneToOne){

                truePositives.add(m.a);
            }
        }

        falseNegative += falseNegatives.size();
        falsePositive += falsePositives.size();
        truePositive += truePositives.size();
        total += from.size();

    }

    /**
     * find the mappings from to to.
     * @param from
     * @param to
     * @return  list sorted by BBJI value.
     */
    List<Mapping> getMappings(DeformableMesh3D from, List<DeformableMesh3D> to){
        List<Mapping> mappings = new ArrayList<>();
        Box3D bounds = from.getBoundingBox();
        double vol = bounds.getVolume();

        for(DeformableMesh3D mesh: to){
            Box3D tob = mesh.getBoundingBox();
            if(!bounds.intersects(tob)){
                continue;
            }
            Box3D ib = bounds.getIntersectingBox(tob);
            double iv = ib.getVolume();
            double ji = iv / (vol + tob.getVolume() - iv);
            mappings.add(new Mapping(from, mesh, ji));
        }
        mappings.sort(Comparator.naturalOrder());

        return mappings;
    }

    public static void main(String[] args) throws IOException {
        for(String comp: args){
            List<String> lines = Files.readAllLines(Paths.get(comp));
            double[] values = { 0, 0, 0, 0};
            for(int j= 0; j<lines.size(); j++) {
                String[] tokens = lines.get(j).split("\\s");
                Path truth = Paths.get(tokens[0]);
                Path predicted = Paths.get(tokens[1]);
                List<Track> tt = MeshReader.loadMeshes(truth.toFile());
                List<Track> pt = MeshReader.loadMeshes(predicted.toFile());

                int last = tt.stream().mapToInt(Track::getLastFrame).max().orElse(-1);
                int first = tt.stream().mapToInt(Track::getFirstFrame).min().orElse(-1);

                CompareMeshFiles cmf = new CompareMeshFiles();
                for (int i = first; i <= last; i++) {
                    final int frame = i;
                    List<DeformableMesh3D> from = tt.stream().filter(t -> t.containsKey(frame)).map(t -> t.getMesh(frame)).collect(Collectors.toList());
                    List<DeformableMesh3D> to = pt.stream().filter(t -> t.containsKey(frame)).map(t -> t.getMesh(frame)).collect(Collectors.toList());
                    cmf.evaluatePrediction(from, to);
                }
                values[0] += cmf.total;
                values[1] += cmf.truePositive;
                values[2] += cmf.falsePositive;
                values[3] += cmf.falseNegative;
                //System.out.println(cmf.total + "\t" + cmf.truePositive + "\t" + cmf.falsePositive + "\t" + cmf.falseNegative);
            }
            System.out.println(values[0] + "\t" + values[1] + "\t" + values[2] + "\t" + values[3] + "\t");
        }



    }

}
