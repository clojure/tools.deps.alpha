package clojure.tools.deps.alpha.util;

import java.lang.Thread;
import javax.inject.Named;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import clojure.java.api.Clojure;
import clojure.lang.IFn;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.transfer.NoTransporterException;

/**
 * Transporter factory for repositories using the s3 protocol.
 */
@Named("s3")
public final class S3TransporterFactory implements TransporterFactory, Service {

    private static class DelayedInstance {
        private static final IFn NEW_TRANSPORTER;

        static {
            IFn REQUIRE = Clojure.var("clojure.core", "require");
            REQUIRE.invoke(Clojure.read("clojure.tools.deps.alpha.util.s3-transporter"));
            NEW_TRANSPORTER = Clojure.var("clojure.tools.deps.alpha.util.s3-transporter", "new-transporter");
        }
    }

    private S3TransporterFactory() {}

    private Object triggerLoad() {
        return DelayedInstance.NEW_TRANSPORTER;
    }

    public void initService(ServiceLocator locator) {
        // Load transporter (and core.async) lazily in background daemon thread
        Thread t = new Thread(new Runnable() { public void run() { triggerLoad(); }});
        t.setDaemon(true);
        t.start();
    }

    public Transporter newInstance(RepositorySystemSession session, RemoteRepository repository) throws NoTransporterException {
        String protocol = repository.getProtocol();
        if("s3".equals(protocol)) {
            return (Transporter) DelayedInstance.NEW_TRANSPORTER.invoke(session, repository);
        } else {
            throw new NoTransporterException(repository);
        }
    }

    public float getPriority() {
        return 5.0f;
    }

}
