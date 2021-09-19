import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * For submitting a collections of jobs to be ran in parallel, with a limited number of jobs executed at once.
 *
 * Created on 9/6/16.
 */
public class JobDistributor implements Callable<Integer> {
    Thread err, stdout;
    final List<String> command;
    final String name;
    JobDistributor(String name, List<String> commands){
        this.name = name;
        this.command = new ArrayList<>(commands);
    }
    public Integer call(){
        ProcessBuilder build = new ProcessBuilder(command);
        Path errFile = Paths.get("log",name + "-err.txt");
        Path outFile = Paths.get("log",name + "-out.txt");
        int exitCode = 0;
        try(OutputStream errStream = Files.newOutputStream(
                errFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        ); OutputStream outStream = Files.newOutputStream(
                outFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        )) {
            Process proc = build.start();
            err = new Thread(
                    new Drain( proc.getErrorStream(),errStream )
            );

            err.setDaemon(true);
            err.start();

            stdout = new Thread(
                    new Drain( proc.getInputStream(), outStream )
            );
            stdout.setDaemon(true);
            stdout.start();
            boolean waiting = true;
            while(waiting) {
                try {
                    proc.waitFor();
                    waiting = false;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            exitCode = proc.exitValue();
            switch(exitCode){
                case 0:
                    System.out.println("success!");
                    break;
                default:
                    System.err.println("job: " + name + " exited with a non-zero exit code");
                    break;
            }

        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        } finally{
            if(err!=null) err.interrupt();
            if(stdout!=null) stdout.interrupt();
        }

        return exitCode;

    }

    public static void main(String[] args){
        ExecutorService service = Executors.newFixedThreadPool(8);
        List<Callable<Integer>> jobs = new ArrayList<>();
        try(BufferedReader reader = Files.newBufferedReader(Paths.get(args[0]))) {
            List<JobDistributor> jd = reader.lines().map(line ->
                            line.split("\\s+")
            ).filter(
                    cmd -> cmd.length > 0
            ).map(stringArray -> new JobDistributor(getName(), Arrays.asList(stringArray))).collect(Collectors.toList());
            jobs.addAll(jd);



        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("could not read job file");
            System.exit(-1);
        }

        try {
            List<Future<Integer>> futures = service.invokeAll(jobs);
            int success = 0;
            int fail = 0;
            for(Future<Integer> future: futures){
                try {
                    if(future.get()!=0){
                        fail++;
                    } else{
                        success++;
                    }
                } catch (ExecutionException e) {
                    e.printStackTrace();
                    fail++;
                }
            }
            System.out.println(""+success + "tasks complete. " + fail + " failed.");
            service.shutdown();
        } catch (InterruptedException e) {
            e.printStackTrace();
            System.out.println("interrupted! now exiting");
            System.exit(-1);
        }

    }

    static String getName(){
        return new Random().ints().limit(5).mapToObj(i ->{

            int v = i<0?(-i)%26:i%26;
            char a = (char)(v + 'a');
            return a+"";
        }).collect(Collectors.joining());
    }

    class Drain implements Runnable{
        final BufferedReader reader;
        final BufferedWriter writer;

        char[] buffer = new char[512];
        Drain(InputStream stream, OutputStream out){
            reader = new BufferedReader(new InputStreamReader(stream, Charset.forName("UTF8")));
            writer = new BufferedWriter(new OutputStreamWriter(out, Charset.forName("UTF8")));
        }
        @Override
        public void run() {
            while(!Thread.interrupted()){
                try{
                    int read = reader.read(buffer, 0, buffer.length);
                    if(read<0){
                        return;
                    }
                    writer.write(buffer, 0, read);
                    writer.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
            }
        }
    }

}
