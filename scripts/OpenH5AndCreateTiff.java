import ij.ImageJ;
import ij.ImagePlus;
import loci.formats.FormatException;
import loci.plugins.BF;
import loci.plugins.in.ImporterOptions;

import java.io.IOException;

public class OpenH5AndCreateTiff {


    public static void main(String[] args) throws IOException, FormatException {

        new ImageJ();
        ImporterOptions options = new ImporterOptions();
        options.setVirtual(true);
        options.setOpenAllSeries(true);
        options.setId("dataset.xml");
        long start = System.currentTimeMillis();
        ImagePlus[] pluses = BF.openImagePlus(options);
        for(ImagePlus plus: pluses){
            plus.show();
        }
        System.out.println(pluses.length + " pluses loaded");
        System.out.println((System.currentTimeMillis() - start ) + " ms elapsed");

    }

}
