package keyvalue;

import org.nustaq.kontraktor.*;
import org.nustaq.kontraktor.impl.ElasticScheduler;
import org.nustaq.kontraktor.remoting.http.rest.RestActorServer;
import org.nustaq.kontraktor.remoting.tcp.TCPActorServer;
import org.nustaq.offheap.FSTAsciiStringOffheapMap;
import static org.nustaq.offheap.FSTAsciiStringOffheapMap.*;
import keyvalue.OffHeapMapExample.*;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

/**
 * Created by ruedi on 15.12.14.
 */
public class KVServer extends Actor {
    FSTAsciiStringOffheapMap<User> memory;

    public Future $init() {
        try {
            new File("/tmp").mkdirs(); // windows
            memory = new FSTAsciiStringOffheapMap<>(
                         "/tmp/storeadvent.mmf",
                         20, // max key length
                         4*GB, // size: (can be very greedy as OS loads on-access)
                         15_000_000 // estimated number of keys
                     );
            memory.getCoder().getConf().registerClass(User.class); // avoid writing full classnames
            if ( memory.getSize() == 0 ) {
                System.out.println("generating sample 15 million records .. pls wait a minute");
                OffHeapMapExample oh = new OffHeapMapExample(); oh.fifteenMillion(memory);
                System.out.println(".. done");
            }
            System.out.println("server intialized");
        } catch (Exception e) { return new Promise<>(null,e); }
        return new Promise<>("yes");
    }

    public Future<User> $get( String uid ) { return new Promise<>( memory.get(uid) ); }

    public void $put( String uid, User value ) { memory.put(uid,value); }

    public Future<Integer> getSize() {
        return new Promise<>(memory.getSize());
    }

    public static void main(String arg[]) throws IOException {
        ElasticScheduler.DEBUG_SCHEDULING = false; // kontraktor beta is chatty ..

        KVServer servingActor = Actors.AsActor(KVServer.class);
        servingActor.$init().onResult(result -> {
            try {
                TCPActorServer.Publish(servingActor, 7777);
                // http://localhost:8088/kvservice/$get/u13
                RestActorServer.Publish("kvservice", 8088, servingActor);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).onError(error -> ((Exception) error).printStackTrace());
    }

    ////////////// spore playground //////////////////////////////////////

    public void $iterateValues(Spore<User, Object> spore) {
        for (Iterator iterator = memory.values(); iterator.hasNext() && ! spore.isFinished(); ) {
            User next = (User) iterator.next();
            spore.remote(next);
        }
        spore.finished();
    }

    public void $onMap(Spore<FSTAsciiStringOffheapMap, Object> spore) {
        spore.remote(memory);
        spore.finished();
    }

}
