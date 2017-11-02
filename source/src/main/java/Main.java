import com.iot4pwc.constants.ConstLib;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
/**
 * The main class for the program. Run: mvn package to get A fat jar.
 * In terminal, run: java -jar servicePlatform-1.0-SNAPSHOT-fat.jar [options] to run the program. Please
 * note that every time a new vertx will be created.
 *
 * options:
 *
 * -sp: when -sp is used, the service platform will be initialized.
 * -dg: when -dg is specified, the data generator will be initialized.
 * if no option is specified, run everything locally.
 */
public class Main {
  public static void main(String[] args) {
    // set system properties
    Properties props = System.getProperties();
    props.setProperty("java.util.logging.config.file", ConstLib.LOGGING_CONFIG);
    props.setProperty("vertx.logger-delegate-factory-class-name", "io.vertx.core.logging.Log4j2LogDelegateFactory");
    // Modify logging configuration here
    Configurator.setLevel("com.iot4pwc.verticles", ConstLib.LOGGING_LEVEL);
    Configurator.setRootLevel(ConstLib.LOGGING_LEVEL);
    Logger logger = LogManager.getLogger(Main.class);

    if (args.length == 0) {
      Vertx vertx = Vertx.vertx();

      DeploymentOptions deploymentOptions = new DeploymentOptions().setInstances(ConstLib.BACKEND_WORKER_POOL_SIZE);
      vertx.deployVerticle("com.iot4pwc.verticles.BackendServer", deploymentOptions);
    }
  }
}