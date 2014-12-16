package keyvalue;

import org.nustaq.kontraktor.Future;
import org.nustaq.kontraktor.impl.ElasticScheduler;
import org.nustaq.kontraktor.remoting.tcp.TCPActorClient;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/**
 * Created by ruedi on 15.12.2014.
 */
public class KVClient {

    public static void main(String arg[]) throws IOException {
        ElasticScheduler.DEBUG_SCHEDULING = false; // kontraktor beta is chatty ..

        Future<KVServer> connect = TCPActorClient.Connect(KVServer.class, "127.0.0.1", 7777)
            .onResult(server -> {
                try {
                    while (true) {
                        benchGet(server,true);
                        benchGet(server,false);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            })
            .onError(error -> System.out.println("connection error " + error));
    }

    private static void benchGet(KVServer server, boolean existing) throws InterruptedException {
        final int numMsg = 1_000_000;
        CountDownLatch latch = new CountDownLatch(numMsg);
        long tim = System.currentTimeMillis();

        for ( int i = 0; i < numMsg; i++ ) {
            if ( existing ) {
                server.$get("u" + latch.getCount()).then((person, err) -> {
                    latch.countDown();
                });
            } else {
                server.$get("norecord").then((person, err) -> {
                    latch.countDown();
                });
            }
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        long dur = System.currentTimeMillis()-tim;
        System.out.print("dur 1 million messages (ms)" + dur);
        System.out.println("- rate get ("+(existing?"existing":"null result")+") per second:"+(numMsg/dur)*1000);
    }
}