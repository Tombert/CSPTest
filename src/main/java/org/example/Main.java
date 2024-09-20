package org.example;

import java.util.concurrent.LinkedBlockingQueue;
import tombert.csp.CSP;

// TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
  public static void main(String[] args) {

    var init = CSP.returned("howdy");

    var a =
        CSP.map(
            x -> {
              return x + " tom";
            },
            init);

    var b =
        CSP.bind(
            x -> {
              var rc = new LinkedBlockingQueue<String>();
              Thread.startVirtualThread(
                  () -> {
                    while (true) {
                      try {
                        Thread.sleep(1000);
                        var z = x + " poop";
                        rc.put(z);
                      } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                      }
                    }
                  });
              return rc;
            },
            a);

    while (true) {
      try {
        var res = b.take();
        System.out.println(res);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
