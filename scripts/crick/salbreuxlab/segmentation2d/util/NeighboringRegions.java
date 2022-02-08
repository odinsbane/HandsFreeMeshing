package crick.salbreuxlab.segmentation2d.util;

public class NeighboringRegions{
    public final int A, B;
    public NeighboringRegions(int a, int b){
        if(a==b){
            throw new IllegalArgumentException("A region does not neighbor itself!");
        } else if (a > b){
            A = b;
            B = a;
        } else{
            A = a;
            B = b;
        }
    }

    public boolean contains(int a){
        return A==a || B==a;
    }

    @Override
    public int hashCode(){
        return A + B;
    }
    @Override
    public  boolean equals(Object o){
        if( o instanceof NeighboringRegions){
            NeighboringRegions nr =(NeighboringRegions)o;
            return (nr.A==A && nr.B==B);
        }
        return false;
    }
}
