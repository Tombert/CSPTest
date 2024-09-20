package tombert.csp;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;
import java.util.function.Supplier;

public class CSP {
    public enum Signals {
        DONE;
    }

    public static <R> BlockingQueue<R> returned(R init) {
        var retChan = new LinkedBlockingQueue<R>();
        retChan.offer(init);
        return retChan;
    }

    public static <R> BlockingQueue<List<R>> chunk(int size, BlockingQueue<R> init) {
        var retChan = new LinkedBlockingQueue<List<R>>();

        Thread.startVirtualThread(() -> {
            var buffer = new ArrayList<R>();
            while (true) {
                try {
                    var current = init.take();
                    buffer.add(current);
                    if (buffer.size() >= size) {
                        retChan.put(buffer);
                    }
                    buffer = new ArrayList<>();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

        });
        return retChan;
    }

    public static <V,R> BlockingQueue<R> bind(Function<V, BlockingQueue<R>> f, BlockingQueue<V> init) {
        var retChan = new LinkedBlockingQueue<R>();
        Thread.startVirtualThread(() -> {
            while (true) {
                try {
                    var current = init.take();
                    var next =  f.apply(current);
                    Thread.startVirtualThread(() -> {
                        while (true) {
                            try {
                                var next2 = next.take();
                                retChan.put(next2);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        return retChan;
    }

    public static <V, R> BlockingQueue<R> map(Function<V, R> f, BlockingQueue<V> init)  {
        var retChan = new LinkedBlockingQueue<R>();
        Thread.startVirtualThread( () -> {
            while (true) {
                try {
                    var current = init.take();
                    var newThing = f.apply(current);
                    retChan.put(newThing);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        return retChan;
    }

    public static <R> BlockingQueue<R> filter(Function<R, Boolean> f, BlockingQueue<R> init) {
        var retChan = new LinkedBlockingQueue<R>();
        Thread.startVirtualThread(() -> {
           while (true) {
               try {
                   var current = init.take();
                   var res = f.apply(current);
                   if (res) {
                       retChan.put(current);
                   }
               } catch (InterruptedException e) {
                   throw new RuntimeException(e);
               }

           }
        });
        return retChan;
    }

    public static BlockingQueue<Signals> timeout(Duration tout) {
        var retChan = new LinkedBlockingQueue<Signals>(1);
        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(tout.toMillis());
                retChan.put(Signals.DONE);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        return retChan;
    }
    public static <V> BlockingQueue<V> go(Supplier<V> f ){
       var retChan = new LinkedBlockingQueue<V>(1);
       Thread.startVirtualThread(() -> {
           var res = f.get();
           try {
               retChan.put(res);
           } catch (InterruptedException e) {
               throw new RuntimeException(e);
           }

       });
       return retChan;
    }

    public static BlockingQueue<Signals> go(Runnable f) {
        var retChan = new LinkedBlockingQueue<Signals>();
        Thread.startVirtualThread(() -> {
            f.run();
            try {
                retChan.put(Signals.DONE);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        return retChan;
    }

    public static void goLoop(Runnable f) {
        Thread.startVirtualThread(() -> {
            while(true) {
                f.run();
                Thread.yield();
            }
        });
    }



    public static BlockingQueue<Object> select(List<BlockingQueue<Object>> channels) {
        @SuppressWarnings("unchecked")
        var chans =  channels.toArray(BlockingQueue[]::new);
        return select(chans);
    }
    @SafeVarargs
    public static  BlockingQueue<Object> select(BlockingQueue<Object> ...channels) {
        var results = new LinkedBlockingQueue<CompletableFuture<Object>>(channels.length);
        for (var chan: channels) {
                var f = CompletableFuture.supplyAsync(() -> {
                    try {
                        return chan.take();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                });
                results.offer(f);

        }
        var blah = results.toArray(CompletableFuture[]::new);
        var retChan = new LinkedBlockingQueue<Object>(1);
        CompletableFuture.anyOf(blah).thenAccept(i -> {
           try {
                   retChan.put(i);
           } catch (InterruptedException e) {
               throw new RuntimeException(e);
           }
        });
        return retChan;
    }

    @SafeVarargs
    public static  CompletableFuture<Object> selectAsync(BlockingQueue<Object> ...channels) {
        var res = select(channels);
        return CompletableFuture.supplyAsync(() -> {
            try {
                return  res.take();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

}