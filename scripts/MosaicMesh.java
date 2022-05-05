import deformablemesh.DeformableMesh3DTools;
import deformablemesh.MeshImageStack;
import deformablemesh.io.MeshReader;
import deformablemesh.io.MeshWriter;
import deformablemesh.track.Track;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.nio.file.*;

import java.awt.Color;

public class MosaicMesh {
    public static void main(String[] args) throws IOException {

        Path image;
        Path meshFile;
        boolean headless;
        if(args.length == 2){
            image = Paths.get(args[0]).toAbsolutePath();
            meshFile = Paths.get(args[1]).toAbsolutePath();
            headless = true;
        } else{
            new ImageJ();
            image = Paths.get(IJ.getFilePath("select image")).toAbsolutePath();
            meshFile = Paths.get(IJ.getFilePath("select mesh file")).toAbsolutePath();
            headless = false;
        }
        String mosaicName = meshFile.getFileName().toString().replace(".bmf", "-mosaic.tif");;
        ImagePlus plus = new ImagePlus(image.toString());
        List<Track> tracks = MeshReader.loadMeshes(meshFile.toFile());
        for(int i = 0; i<tracks.size(); i++){
            tracks.get(i).setColor( new Color( i + 1 ) );
        }
        MeshImageStack stack = new MeshImageStack(plus);
        ImagePlus mosaic = DeformableMesh3DTools.createMosaicRepresentation(stack, plus, tracks);
        if(headless){
            IJ.save(
                    mosaic,
                    meshFile.getParent().resolve(mosaicName).toString()
            );
        } else{
            mosaic.setTitle(mosaicName);
            mosaic.show();
        }
    }
}
