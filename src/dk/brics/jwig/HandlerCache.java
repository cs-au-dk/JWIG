package dk.brics.jwig;

import dk.brics.jwig.util.RandomString;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A special (simple) cache for regenerated responses. Regenerated responses are stored in this cache for
 * retrieval by the handlers web method. Responses are automatically wiped from this cache after
 * the first retrieval or after 1 minute (whatever comes first).
 *
 * Any XML response is stored in this cache, if the jwig-regenerate header is set. The store response
 * is the final response to the client (not the one stored in the normal cache). This means that
 * handlers generated above the normal cache priority can be retrieved through this cache
 */
public class HandlerCache {
    public ConcurrentHashMap<String, ResponseEntry> cachedResponses = new ConcurrentHashMap<String, ResponseEntry>();
    private Timer cleanerThread =  new Timer(true);

    public HandlerCache() {
        cleanerThread.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                Set<String> toRemove = new HashSet<String>();
                for (Map.Entry<String, ResponseEntry> e : cachedResponses.entrySet()) {
                    if (now - e.getValue().timestamp > 60000) {
                        toRemove.add(e.getKey());
                    }
                }
                for (String s : toRemove) {
                    cachedResponses.remove(s);
                }
            }
        },0,60000);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        cleanerThread.cancel();
    }

    public String createTicket(Response res) {
        String r = RandomString.get(15);
        cachedResponses.put(r, new ResponseEntry(res));
        return r;
    }

    public Response removeResponse(String r) {
        if (r == null) return null;
        ResponseEntry remove = cachedResponses.remove(r);
        return remove == null? null : remove.response;
    }

    public boolean hasResponse(String r) {
        if (r == null) return false;
        return cachedResponses.containsKey(r);
    }

    private class ResponseEntry {
        private ResponseEntry(Response response) {
            this.response = response;
        }

        Response response;
        long timestamp = System.currentTimeMillis();
    }


}
