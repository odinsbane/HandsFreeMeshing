package crick.salbreuxlab.segmentation2d;

import crick.salbreuxlab.segmentation2d.labelling.DistanceTransformer;
import crick.salbreuxlab.segmentation2d.util.NeighboringRegions;
import crick.salbreuxlab.segmentation2d.util.SnakeInterface;
import crick.salbreuxlab.segmentation2d.util.SortedIntSet;
import deformablemesh.util.connectedcomponents.ConnectedComponents2D;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.awt.Rectangle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Receives a skeletized image of an epithelial cell. Separates the skeleton into regions. Locates Vertexes and
 * interfaces.
 *
 * Created by smithm3 on 05/06/18.
 */
public class TwoDSegmentationSnake {
    ImageProcessor proc;
    public List<Vertex> vertices = new ArrayList<>();
    List<TwoDLabeledRegion> regions;
    Map<NeighboringRegions, List<int[]>> interfaces;
    ConnectedComponents2D cc2d;
    TwoDLabeledRegion skeletonRegion;
    Map<Integer, List<int[]>> deadEnds;
    Map<NeighboringRegions, List<Vertex> > interfaceToVertex;
    Map<Integer, List<double[]>> segmented = new HashMap<>();


    Map<Integer, List<Vertex>> regionToVertexes = new HashMap<>();
    Map<Vertex, SortedIntSet> vertexToRegion = new HashMap<>();

    List<TwoDLabeledRegion> vertexRegions;

    Map<Integer, Set<NeighboringRegions>> regionToInterfaces;

    public TwoDSegmentationSnake(ImageProcessor proc){
        this.proc = proc;
    }

    /**
     * Labels the skeleton network where 0 represents the regions being labeled.
     *
     */
    public void labelRegions(){
        ImageProcessor threshed = new ByteProcessor(proc.getWidth(), proc.getHeight());
        int n = proc.getHeight()*proc.getWidth();
        int w = proc.getWidth();
        List<int[]> skeletonPts = new ArrayList<>();
        for(int i = 0; i<n; i++){
            if(proc.get(i)==0){
                threshed.set(i, 255);
            } else{
                skeletonPts.add(new int[]{i%w, i/w});
            }
        }
        System.out.println(skeletonPts.size() + "/" + n);
        skeletonRegion = new TwoDLabeledRegion(0, skeletonPts);
        cc2d = new ConnectedComponents2D(threshed);
        cc2d.process();
        regions = cc2d.getRegions().entrySet().stream().map(e->new TwoDLabeledRegion(e.getKey(), e.getValue())).collect(Collectors.toList());
    }




    List<Vertex> issues = new ArrayList<>();

    /**
     *
     * @param label
     * @return
     */
    public List<SnakeInterface> freeSegmentRegion(int label){
        List<SnakeInterface> boundary = new ArrayList<>();
        return boundary;
    }


    public List<SnakeInterface> segmentRegion2(int label){
        List<SnakeInterface> boundary = new ArrayList<>();
        List<Vertex> vertices = regionToVertexes.get(label);
        if(vertices.size()<=1){
            return freeSegmentRegion(label);
        }

        Map<Vertex, List<Vertex>> adjacent = new HashMap<>();
        for(int i = 0; i<vertices.size(); i++){
            Vertex a = vertices.get(i);
            for(int j = i+1; j<vertices.size(); j++){
                Vertex b = vertices.get(j);
                List<NeighboringRegions> nrs = getMutualInterfaces(
                        a, b
                ).stream().filter(
                        nr->nr.contains(label)
                ).collect(Collectors.toList());

                if(nrs.size()>0){
                    adjacent.computeIfAbsent(a, k->new ArrayList<>()).add(b);
                    adjacent.computeIfAbsent(b, k->new ArrayList<>()).add(a);
                }
            }
        }
        boolean simple = true;
        if(adjacent.size() == vertices.size()){
            for(List<Vertex> neighbored: adjacent.values()){
                if(neighbored.size() != 2){
                    simple = false;
                }
            }
            if(simple){
                return simpleSegment(label, adjacent);
            } else{
                System.out.println(vertices.size());
                issues.addAll(adjacent.keySet());
            }
        } else{
            System.out.println("No vertex neighbors! " + vertices.size() + " : " + adjacent.size() + ", " + label);
            if(vertices.size()>10) {
                for (Vertex v : vertices) {
                    //issues.add(v);
                }
            }
        }
        if(!simple) {
            System.out.println("challenging");
        }
        return boundary;


    }

    /**
     * CW Ring about the center, used for 'entering' a region and determine
     */
    final static int[][] STEPS = {
            {-1, -1},
            {0, -1},
            {1, -1},
            {1, 0},
            {1, 1},
            {0, 1},
            {-1, 1},
            {-1, 0}
    };
    /**
     * Starting from the top left and going clockwise.
     *
     * ( -1, -1 ) -> (0, -1) -> (1, -1) -> ( 1, 0 ) -> (1, 1) -> ( 0, 1 ) -> ( -1, 1) -> ( -1, 0) and repeat.
     *
     *
     *
     * @param from
     * @param to
     * @return
     */

    int[] enterVertex(int[] from, int[] to){
        int dx = from[0] - to[0];
        int dy = from[1] - to[1];
        if(dx*dx + dy*dy > 2) return new int[]{};
        int i;
        for( i = 0; i<8; i++){
            if (dx == STEPS[i][0] && dy == STEPS[i][1]){
                break;
            }
        }
        int[] left_to_right = new int[2];
        int found = 0;
        for(int j = 1; j<8; j++){
            int[] delta = STEPS[(i + j)%8];
            int xi = to[0] + delta[0];
            int yi = to[1] + delta[1];
            int l;
            if(xi<0 || yi<0 || xi >= cc2d.getWidth() || yi>=cc2d.getHeight()){
                l = -1;
            } else {
                l = cc2d.get(to[0] + delta[0], to[1] + delta[1]);
            }
            if(l!=0){

                if( found==0 || left_to_right[0]!=l){
                    if(found==left_to_right.length){
                        int[] old = left_to_right;
                        left_to_right = new int[old.length + 1];
                        System.arraycopy(old, 0, left_to_right, 0, old.length);
                    }
                    left_to_right[found++] = l;
                }
            }
        }
        /*
        */
        if(left_to_right[left_to_right.length-1] < 0 ){
            for(int j = -1; j<2; j++){
                for(int k =-1; k<2; k++){
                    if(j==dx && k==dy){
                        System.out.print("X");
                    } else {
                        //System.out.print(cc2d.get(to[0] + j, to[1] + k) + " ");
                    }
                }
                System.out.println();
            }
        }
        return found>1 ? left_to_right : new int[]{left_to_right[0]};
    }
    int[] enterNeighboringPixel(int[] from, int[] to){

        int dx = from[0] - to[0];
        int dy = from[1] - to[1];
        if(dx*dx + dy*dy > 2) return new int[]{};
        int i;
        for( i = 0; i<8; i++){
            if (dx == STEPS[i][0] && dy == STEPS[i][1]){
                break;
            }
        }
        int[] left_to_right = new int[2];
        int found = 0;
        for(int j = 1; j<8; j++){
            int[] delta = STEPS[(i + j)%8];
            int l = cc2d.get(to[0] + delta[0], to[1] + delta[1]);
            if(l!=0){
                if( found==0 || left_to_right[0]!=l){

                    left_to_right[found++] = l;
                    if(found==2){
                        return left_to_right;
                    }
                }
            }
        }

        throw new RuntimeException("Entered non-two neighbor regions. " + to[0] + ", " + to[1]);

    }

    /**
     * This is 'simple' because the vertex map Maps
     * @param label
     * @param adjacentVertexes
     * @return
     */
    public List<SnakeInterface> simpleSegment(int label, Map<Vertex, List<Vertex>> adjacentVertexes){
        List<SnakeInterface> ret = new ArrayList<>();
        List<Vertex> remaining = new ArrayList<>(adjacentVertexes.keySet());
        Vertex start = remaining.get(0);
        remaining.remove(start);
        List<Vertex> pair = adjacentVertexes.get(start);

        Vertex one = pair.get(0); //either way is good.
        Vertex two = pair.get(1);
        SnakeInterface sone = getConnectingNeighboringRegions(start, one);

        SnakeInterface stwo = getConnectingNeighboringRegions(start, two);

        Vertex current;
        SnakeInterface alt;
        if(sone.right==label){
            //going clockwise around.
            current = one;
            ret.add(sone);
            alt = stwo;
        } else {
            current = two;
            ret.add(stwo);
            alt = sone;
        }


        remaining.remove(current);

        Vertex previous = start;
        while(remaining.size()> 0){

            pair = adjacentVertexes.get(current);
            Vertex next;
            if(pair.get(0).equals(previous)){
                next = pair.get(1);
            } else{
                next = pair.get(0);
            }
            ret.add(getConnectingNeighboringRegions(current, next));

            previous = current;
            current = next;
            remaining.remove(next);
        }
        ret.add(getConnectingNeighboringRegions(current, start));
        return ret;
    }

    SnakeInterface getConnectingNeighboringRegions(Vertex a, Vertex b){

        SortedIntSet ar = vertexToRegion.get(a);
        SortedIntSet br = vertexToRegion.get(b);
        SortedIntSet nrIds = ar.intersection(br);
        NeighboringRegions nr = new NeighboringRegions(nrIds.get(0), nrIds.get(1));

        List<int[]> checked = interfaces.get(nr);
        if(checked==null){
            int[] ptA = new int[]{a.x, a.y};
            int[] ptB = new int[]{b.x, b.y};
            //no interface between the two points. Connection is just two vertex points.
            int[] ltr = enterVertex( ptA, ptB);
            if(ltr.length == 0){
                ltr = new int[]{-1, -1};
                System.out.println("couldn't connect: " + a + " \t " + b);
                issues.add(b);
            }
            if(ltr[ltr.length-1]<0){
                System.out.println(Arrays.toString(ltr));
            }

            SnakeInterface si = new SnakeInterface(a, b, ltr[0], ltr[ltr.length-1]);
            si.addAll(Arrays.asList(ptA, ptB));
            return si;
        }
        checked = new ArrayList<>(checked);
        int[] last = new int[]{a.x, a.y};
        int dx;
        int dy;
        List<int[]> ordered = new ArrayList<>();
        ordered.add(last);

        int right = -2;

        do {
            boolean found = false;
            for(int[] pt: checked){
                int[] entered = enterNeighboringPixel(last, pt);

                if(entered.length==2){
                    if(right == -2){
                        //first time.
                        right = entered[1];
                        last = pt;
                        found = true;
                        break;
                    }else{
                         //we already have a 'right'.
                        if(right==entered[1]){
                            last = pt;
                            found = true;
                            break;
                        }
                    }
                }
            }
            if(!found){
                //this is bad!
                throw new RuntimeException("could not connect Vertexs: " + a + " to " + b);
            }
            ordered.add(last);
            checked.remove(last);
            dx = b.x - last[0];
            dy = b.y - last[1];
        } while(dx*dx + dy*dy > 2);
        if(right==-2){
            //this means, there was an interface, *but* that interface was connecting neighboring vertexes
            /*
                A I 0
                0 B 0
                0 0 0
             */
            //hence the while loop never runs. fix by switching to 'do while'?
        }
        ordered.add(new int[]{b.x, b.y});
        int left = right==nr.A ? nr.B : nr.A;
        SnakeInterface si =  new SnakeInterface(a, b, left, right);

        si.addAll(ordered);
        return si;


    }

    public List<NeighboringRegions> getMutualInterfaces(Vertex a, Vertex b){
        List<NeighboringRegions> ret = new ArrayList<>();
        SortedIntSet intersection = vertexToRegion.get(a).intersection(vertexToRegion.get(b));
        for(int i = 0; i<intersection.size()-1; i++){
            for(int j = i+1; j<intersection.size(); j++){
                ret.add(new NeighboringRegions(intersection.get(i), intersection.get(j)));
            }
        }
        return ret;
    }

    public boolean touches(Vertex v, NeighboringRegions r){
        SortedIntSet set = vertexToRegion.get(v);

        return set.contains(r.A) && set.contains(r.B);

    }
    public void pruneDeadEnds(){
        List<int[]> px = skeletonRegion.getPx();
        deadEnds = new HashMap<>();
        List<int[]> leftovers = new ArrayList<>();
        for(int[] pt: px){
            SortedIntSet neighbors = getNeighbors(pt[0], pt[1], cc2d);
            if(neighbors.size()==1) {
                //single neighbor, it is completely surrounded by one region.
                List<int[]> pts = deadEnds.computeIfAbsent(neighbors.get(0),i->new ArrayList<>());
                pts.add(pt);
            }else if(neighbors.size()==2 && neighbors.contains(-1)){
                    deadEnds.computeIfAbsent(neighbors.get(1),i->new ArrayList<>()).add(pt);
            } else{
                leftovers.add(pt);
            }

        }

        for(int label: deadEnds.keySet()){
            if(label<=0) continue;
            List<int[]> pxs = deadEnds.get(label);
            for(int[] p: pxs){
                cc2d.label(p, label);
            }
        }
        px.clear();
        px.addAll(leftovers);


    }
    public boolean fourVertex(int x, int y, ConnectedComponents2D cc2d){
        int xh = x+1;
        int yh = y+1;
        if(xh<cc2d.getWidth() && yh<cc2d.getHeight()){
            if(cc2d.get(xh, y)==0 && cc2d.get(x, yh)==0 && cc2d.get(xh, yh)==0){
                return true;
            }
        }

        return false;
    }

    public SortedIntSet getFourVertexNeighbors(int x, int y, ConnectedComponents2D cc2d){
        SortedIntSet labels = new SortedIntSet();
        for(int i = -1; i<3; i++){

            if(x + i < 0 || x +i >= proc.getWidth()){
                labels.add(-1);
                //add a region that is "out of bounds";
                continue;
            }
            for(int j = -1; j<3; j++){

                if(y + j < 0 || y +j >= proc.getHeight()){
                    labels.add(-1);
                    continue;
                }
                int s = cc2d.get(x +i, y + j);
                if(s!=0){
                    labels.add(s);
                }
            }
        }

        return labels;
    }


    /**
     * A vertex is defined as the intersection of 3 or more regions.
     */
    public void findVertices2(){
        interfaces = new HashMap<>();
        List<int[]> px = skeletonRegion.getPx();
        interfaceToVertex = new HashMap<>();
        regionToVertexes = new HashMap<>();
        regionToInterfaces = new HashMap<>();
        for(int[] pt: px){

            SortedIntSet neighbors = getNeighbors(pt[0], pt[1], cc2d);
            if(neighbors.size()==1) {
                System.out.println("warning: dead end found at " + pt[0] + ", " + pt[1]);
                //single neighbor, it is completely surrounded by one region.
            } else if(neighbors.size()==2){
                if(neighbors.contains(-1)){
                    //dead end pixel.
                    System.out.println("warning: dead end found at " + pt[0] + ", " + pt[1]);

                } else {
                    NeighboringRegions nr = new NeighboringRegions(neighbors.get(0), neighbors.get(1));

                    if(fourVertex(pt[0], pt[1], cc2d)){
                        Vertex v = new Vertex(pt[0], pt[1], 9);
                        SortedIntSet extendedNeighbors = getFourVertexNeighbors(pt[0], pt[1], cc2d);
                        vertexToRegion.put(v, extendedNeighbors);
                        vertices.add(v);
                        for(int i = 0; i<extendedNeighbors.size(); i++){
                            Integer r = extendedNeighbors.get(i);
                            for(int j = i+1; j<extendedNeighbors.size(); j++){
                                Integer n = extendedNeighbors.get(j);
                                NeighboringRegions enr = new NeighboringRegions(r, neighbors.get(j));
                                interfaceToVertex.computeIfAbsent( enr, k->new ArrayList<>()).add(v);


                                //regionToInterfaces.computeIfAbsent(r, k->new HashSet<>()).add(nr);
                                //regionToInterfaces.computeIfAbsent(n, k->new HashSet<>()).add(nr);

                            }
                            regionToVertexes.computeIfAbsent(r, k->new ArrayList<>()).add(v);
                        }

                    } else{
                        //interface pixels are non-vertex.
                        interfaces.computeIfAbsent(nr, i -> new ArrayList<>()).add(pt);
                    }
                }
            } else if(neighbors.size()>2){
                //vertex!!!
                int mc = getMembraneCount(pt[0], pt[1], cc2d);
                Vertex v = new Vertex(pt[0], pt[1], mc);
                vertices.add(v);
                vertexToRegion.put(v, neighbors);
                int s = 0;

                for(int i = 0; i<neighbors.size(); i++){
                    Integer r = neighbors.get(i);
                    for(int j = i+1; j<neighbors.size(); j++){
                        Integer n = neighbors.get(j);
                        NeighboringRegions nr = new NeighboringRegions(r, neighbors.get(j));
                        //interfaces.computeIfAbsent( nr, k->new ArrayList<>() ).add(pt);
                        //interace pixels are non-vertex.
                        interfaceToVertex.computeIfAbsent( nr, k->new ArrayList<>()).add(v);
                        regionToInterfaces.computeIfAbsent(r, k->new HashSet<>()).add(nr);
                        regionToInterfaces.computeIfAbsent(n, k->new HashSet<>()).add(nr);

                    }
                    regionToVertexes.computeIfAbsent(r, k->new ArrayList<>()).add(v);
                }

            }
        }
    }

    private int getMembraneCount(int x, int y, ConnectedComponents2D cc2d) {
        int sum = 0;
        int w = cc2d.getWidth();
        int h = cc2d.getHeight();
        for(int i = -1; i<2; i++){
            int xi = x+i;
            if(xi<0 || xi==w){
                continue;
            }
            for(int j = -1; j<2; j++){
                int yi = y + j;
                if(yi<0 || yi == h){
                    continue;
                }
                if(cc2d.get(xi, yi) == 0){
                    sum++;
                }
            }

        }
        return sum;
    }

    /**
     * Finds all the skeleton intersections assumed to be vertexes.
     */
    public void findVertices(){
        if(cc2d == null){
            labelRegions();
        }
        //find all of the non-border vertexes.
        ImageProcessor vertProc = new ShortProcessor(proc.getWidth(), proc.getHeight());
        for(int i = 1; i<proc.getWidth()-1; i++){
            for(int j = 1; j<proc.getHeight()-1; j++){

                int c = countBorderingPoints(proc, i,j);
                if(c>2){

                    vertices.add(new Vertex(i,j,c));
                    vertProc.set(i, j, vertices.size());

                }
            }
        }

        findEdgeVertexes(vertProc, vertices);
        //finds all of the touching vertexes.
        vertexRegions = labelRegions(vertProc);

        // vertices is a list of vertexes
        List<Vertex> finished = new ArrayList<>();
        for(TwoDLabeledRegion region: vertexRegions){
            List<Vertex> group = new ArrayList<>(region.px.size());
            //accumulates all of the Vertexes found in the clustered region.
            for(int[] p: region.px){
                group.add(vertices.get(vertProc.get(p[0], p[1]) - 1));
            }

            finished.addAll(reduceVertices(cc2d, group));
        }



        for(Vertex vert: finished){
            SortedIntSet neighbors = getNeighbors(vert, cc2d);
            vertexToRegion.put(vert, neighbors);
            for(int i = 0; i<neighbors.getCount(); i++){
                List<Vertex> vs = regionToVertexes.computeIfAbsent(neighbors.get(i), key-> new ArrayList<>());
                vs.add(vert);
            }
        }
        vertices = finished;



    }

    void findEdgeVertexes(ImageProcessor vertProc, List<Vertex> vertices){
        //TODO check the edges.
        int top = proc.getHeight() - 1;
        int right = proc.getWidth() -1;
        int start = vertices.size();
        for(int i = 0; i<proc.getWidth(); i++){
            if(proc.get(i, 0)!=0) {
                //possible bottom edge.
                int sum = 0;
                if (i > 0) {
                    sum += proc.get(i - 1, 1) == 0 ? 0 : 1;
                }
                sum += proc.get(i, 1) == 0 ? 0 : 1;
                if (i < right) {
                    sum += proc.get(i + 1, 1);
                }
                if(sum >= 1){
                    vertices.add(new Vertex(i, 0, sum));
                    vertProc.set(i, 0, vertices.size());
                }
            }

            if(proc.get(i, top)!=0){
                //possible top edge.
                int sum = 0;
                if (i > 0) {
                    sum += proc.get(i - 1, top-1) == 0 ? 0 : 1;
                }
                sum += proc.get(i, top - 1) == 0 ? 0 : 1;
                if (i < right) {
                    sum += proc.get(i + 1, top - 1);
                }
                if(sum >= 1){
                    vertices.add(new Vertex(i, top, sum));
                    vertProc.set(i, top, vertices.size());
                }
            }
        }

        //remove the ends since we've scanned them above.
        for(int j = 1; j<proc.getHeight()-1; j++){
            if(proc.get(0, j)!=0) {
                //possible right edge.
                int sum = 0;
                if (j > 0) {
                    sum += proc.get(1, j-1) == 0 ? 0 : 1;
                }
                sum += proc.get(1, j) == 0 ? 0 : 1;
                if (j < top) {
                    sum += proc.get(1, j+1);
                }
                if(sum >= 1){
                    vertices.add(new Vertex(0, j, sum));
                    vertProc.set(0, j, vertices.size());
                }
            }

            if(proc.get(right, j)!=0) {
                //possible right edge.
                int sum = 0;
                if (j > 0) {
                    sum += proc.get( right - 1, j-1) == 0 ? 0 : 1;
                }
                sum += proc.get( right - 1 , j) == 0 ? 0 : 1;
                if (j < top) {
                    sum += proc.get( right - 1, j+1);
                }
                if(sum >= 1){
                    vertices.add(new Vertex(right, j, sum));
                    vertProc.set(right, j, vertices.size());
                }
            }
        }


        int end = vertices.size();

    }


    /**
     * Creates a set of snakes,
     */
    public void snakeConnections(){
        //goes through all regions.
        for(Integer i: regionToVertexes.keySet()){
            List<Vertex> vs = regionToVertexes.get(i);
            //go through each region vertex associated to a region.
            for(int k = 0; k<vs.size(); k++){
                SortedIntSet a = vertexToRegion.get(vs.get(k));
                for(int j = k+1; j<vs.size(); j++){
                    SortedIntSet b = vertexToRegion.get(vs.get(j));
                    SortedIntSet c = a.intersection(b);

                    if(c.getCount()>=2){
                        //connections.add(new ConnectingVertexes(vs.get(k), vs.get(j)));
                    }
                }
            }
        }
        //connections.forEach(con->{
         //   snakeConnection(con, cc2d);
        //});


    }

    /**
     * Reduces the number of redundant vertexes.
     *
     * @param labeledRegions Contains the labelled image and labelled regions.
     * @param cluster
     * @return
     */
    List<Vertex> reduceVertices(ConnectedComponents2D labeledRegions, List<Vertex> cluster){
        if(cluster.size()==1) return cluster;


        SortedIntSet labels = new SortedIntSet();

        Queue<SortedIntSet> sets = new PriorityQueue<>(cluster.size(), Comparator.comparingInt(SortedIntSet::size).reversed());
        Map<SortedIntSet, Vertex> comparison = new HashMap<>();

        for(Vertex v: cluster){
            SortedIntSet set = getNeighbors(v, labeledRegions);
            labels.add(set);
            sets.add(set);
            comparison.put(set, v);
        }


        List<Vertex> complete = new ArrayList<>();
        while(comparison.size()>0){
            SortedIntSet set = sets.poll();
            complete.add(comparison.get(set));
            comparison.remove(set);
            for(SortedIntSet s2: sets){
                if(set.contains(s2)){
                    comparison.remove(s2);
                }
            }
            sets.clear();
            sets.addAll(comparison.keySet());
        }


        return complete;
    }


    /**
     * Finds the set of labelled regions neighboring the vertex.
     *
     * @param v vertex of interest.
     * @param proc region labelled processor.
     * @return a set of labelled regions.
     */
    SortedIntSet getNeighbors(Vertex v, ConnectedComponents2D proc){
        return getNeighbors(v.x, v.y, proc);
    }

    /**
     * Finds all off the neighbors of the about the pixel at (x,y). If the point borders an edge, the -1 is
     * included.
     *
     * @param x
     * @param y
     * @param proc
     * @return
     */
    SortedIntSet getNeighbors(int x, int y, ConnectedComponents2D proc){
        SortedIntSet labels = new SortedIntSet();
        for(int i = -1; i<2; i++){

            if(x + i < 0 || x +i >= proc.getWidth()){
                labels.add(-1);
                //add a region that is "out of bounds";
                continue;
            }
            for(int j = -1; j<2; j++){

                if(y + j < 0 || y +j >= proc.getHeight()){
                    labels.add(-1);
                    continue;
                }
                int s = proc.get(x +i, y + j);
                if(s!=0){
                    labels.add(s);
                }
            }
        }

        return labels;
    }
    /**
     * Finds the number of bordering pixels that are not zero about the point of interest (POI) x,y. If the POI value is
     * zero, then it returns 0, otherwise returns the number of non-zero neighboring pixels.
     *
     * @param proc skeletonized processor
     * @param x POI x-coordinate.
     * @param y POI y-coordinate.
     *
     * @return 0 if the POI is not skeleton, or 1-8 for the number of bordering pixels in a 3x3 region that are skeleton.
     */
    public int countBorderingPoints(ImageProcessor proc, int x, int y){
        int sum=0;
        if(proc.get(x,y)==0){
            return 0;
        }
        for(int i = -1; i<2; i++){
            for(int j = -1; j<2; j++){
                sum += proc.get(x + i, y + j)==0?0:1;
            }
        }
        return sum-1;
    }

    /**
     * Contains bounds checking.
     *
     * @param x
     * @param y
     * @param labelA
     * @param labelB
     * @param labeled
     * @return
     */
    boolean interfaceOfRegions2(int x, int y, int labelA, int labelB, ConnectedComponents2D labeled){
        int wb = labeled.getWidth()-1;
        int hb = labeled.getHeight()-1;

        if(x<0 || x>wb || y<0 || y>hb){
            return false;
        }

        int px = labeled.get(x, y);
        if(px!=0) return false;

        boolean aFound = false;
        boolean bFound = false;


        for(int i = -1; i<=1; i++){
            if(x+i < 0 || x+i > wb){
                continue;
            }
            for(int j = -1; j<=1; j++){
                if(y + j < 0 || y + j > hb){
                    continue;
                }
                int p = labeled.get(x +i, y + j);
                aFound |= p==labelA;
                bFound |= p==labelB;

                if(aFound&&bFound){
                    return true;
                }
            }
        }

        return false;


    }

    /**
     * Checks if the point x,y is an interface between two regions labelA, labelB. Assumes the region cannot be near
     * and edge.
     *
     * @param x
     * @param y
     * @param labelA
     * @param labelB
     * @param labeled created the region.
     * @return
     */
    boolean interfaceOfRegions(int x, int y, int labelA, int labelB, ConnectedComponents2D labeled){
       int px = labeled.get(x, y);
       if(px!=0) return false;

        boolean aFound = false;
        boolean bFound = false;

        for(int i = -1; i<=1; i++){
            for(int j = -1; j<=1; j++){
                int p = labeled.get(x +i, y + j);

                aFound |= p==labelA;
                bFound |= p==labelB;

                if(aFound&&bFound){
                    return true;
                }
            }
        }

        return false;


    }

    Rectangle getBoundingBox(List<int[]> pts){
        int lowx = Integer.MAX_VALUE;
        int lowy = Integer.MAX_VALUE;
        int highx = 0;
        int highy = 0;

        for(int[] pt: pts){
            lowx = pt[0]<lowx?pt[0]:lowx;
            highx = pt[0]>highx?pt[0]:highx;
            lowy = pt[1]<lowy?pt[1]:lowy;
            highy = pt[1]>highy?pt[1]:highy;
        }

        return new Rectangle(lowx, lowy, highx-lowx, highy-lowy);
    }


    /**
     * For labeling an image where not equal to zero is the threshold criteria.
     *
     * @param proc
     * @return
     */
    static List<TwoDLabeledRegion> labelRegions(ImageProcessor proc){
        ImageProcessor threshed = new ByteProcessor(proc.getWidth(), proc.getHeight());
        int n = proc.getHeight()*proc.getWidth();
        for(int i = 0; i<n; i++){
            if(proc.get(i)!=0){
                threshed.set(i, 255);
            }
        }
        ConnectedComponents2D cc2d = new ConnectedComponents2D(proc);
        Map<Integer, List<int[]>> points = cc2d.getRegions();
        return points.entrySet().stream().filter(e->e.getValue().size()>0).map(e-> new TwoDLabeledRegion(e.getKey(), e.getValue())).collect(Collectors.toList());

    }

    /**
     * Writes a distance transform to the provided imageprocessor, assuming this TwoDSegmentationSnake.findVertices has
     * been called
     *
     * @param levels number of graduations to be labelled with.
     * @param max any distance exceeding max will have the same label.
     * @param proc where the image will be drawn.
     * @param shift number of labels already occupied.
     *
     */
    public void distanceTransformCategorical(int levels, int max, ImageProcessor proc, int shift){
        DistanceTransformer dt = new DistanceTransformer(levels, max, shift);
        for(TwoDLabeledRegion region: regions){
            dt.categoricalLabelRegion(region, proc);
        }

    }

    /**
     * This tranform allows bitwise regions to overlap.
     *
     * @param levels
     * @param max
     * @param proc
     * @param shift
     */
    public void distanceTransformActual(int levels, int max, ImageProcessor proc, int shift){
        DistanceTransformer dt = new DistanceTransformer(levels, max, shift);
        for(TwoDLabeledRegion region: regions){
            dt.distanceLabelRegion(region, proc);
        }

    }

    public ImageProcessor getLabelledProcessor() {
        int w = cc2d.getWidth();
        int h = cc2d.getHeight();
        ImageProcessor p = new ShortProcessor(cc2d.getWidth(), cc2d.getHeight());
        for(int i = 0; i<w*h; i++){
            p.set(i, cc2d.get(i));
        }
        return p;
    }

    public Map<Integer,List<int[]>> getLabelledRegions() {
        return cc2d.getRegions();
    }

    public void snakeRegions() {
        for(Integer label:regionToInterfaces.keySet()) {
            if (label <= 0) continue;
            Set<NeighboringRegions> nrs = regionToInterfaces.get(label);
            boolean touches_edge = false;
            for (NeighboringRegions nr : nrs) {
                if (nr.contains(-1)) {
                    touches_edge = true;
                    break;
                }
            }

            //if (touches_edge) continue;
            List<SnakeInterface> interfaces = segmentRegion2(label);
            if(interfaces.size()>0){
                for(int i = 0; i < interfaces.size()-1; i++){
                    SnakeInterface f1 = interfaces.get(i);
                    SnakeInterface f2 = interfaces.get(i+1);
                    if(f1.right != f2.right){
                        System.out.println("wrong winding!" + i + ", " + (i+1));
                        System.out.println("\t" + f1);
                        System.out.println("\t" + f2);
                    }
                    int[] head = f1.points.get(f1.points.size()-1);
                    int[] tail = f2.points.get(0);
                    int dx = head[0] - tail[0];
                    int dy = head[1] - tail[1];
                    if(dx*dx + dy*dy > 0){
                        System.out.println(i + ", " + (i+1) +  " :: " + f1.equals(f2));
                        System.out.println("discontinuous interface." + "\t" + f1 + "\n\t\t" + f2);
                    }
                }
                List<double[]> snake = new ArrayList<>();
                for(SnakeInterface face: interfaces){
                    List<int[]> points = face.points;
                    for(int i = 0; i<points.size() - 1; i++){
                        int[] pt = points.get(i);
                        //snakes go to the center of the snake.
                        snake.add(new double[]{pt[0] + 0.5, pt[1] + 0.5});
                    }
                }

                segmented.put(label, snake);
            }
        }
    }

    private boolean regionTouchesEdge(List<int[]> region) {
        int w = proc.getWidth();
        int h = proc.getHeight();

        for(int[] px: region){
            if(px[0]<0 || px[1]<0 || px[0]>=w || px[1] >= h){
                return true;
            }
        }
        return false;
    }
}


