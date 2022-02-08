package crick.salbreuxlab.segmentation2d.util;

import crick.salbreuxlab.segmentation2d.Vertex;

import java.util.ArrayList;
import java.util.List;

/**
 * This represents an interface between 2 regions. It runs between 2 vertexes. Travelling from the start towards the end
 * defines the left/right respectivel.
 *
 * Is it possible to have a directed
 */
public class SnakeInterface{
    final Vertex start, end;
    public final int left, right;
    public List<int[]> points = new ArrayList<>();
    public SnakeInterface(Vertex start, Vertex end, int left, int right){
        points = new ArrayList<>();
        this.start = start;
        this.end = end;
        this.left = left;
        this.right = right;
    }


    public void addAll(List<int[]> points){
        this.points.addAll(points);
    }
    @Override
    public int hashCode(){
        return start.hashCode() + end.hashCode() + left + right;
    }
    @Override
    public boolean equals(Object o){
        if(o instanceof SnakeInterface){
            SnakeInterface i = (SnakeInterface)o;
            return i.start.equals(start) && i.end.equals(end) && i.left == left && i.right == right;
        }
        return false;
    }

    @Override
    public String toString(){
        return "SnakeInterface( " + start + ", " + end + ", " + left + ", " + right + " )" ;
    }

}
