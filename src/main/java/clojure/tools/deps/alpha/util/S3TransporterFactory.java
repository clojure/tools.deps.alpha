package clojure.tools.deps.alpha.util;

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

/**
 * Transporter factory for repositories using the s3 protocol.
 */
@Named("s3")
public final class S3TransporterFactory implements TransporterFactory, Service {

    static {
        IFn REQUIRE = Clojure.var("clojure.core", "require");
        REQUIRE.invoke(Clojure.read("clojure.tools.deps.alpha.util.s3-transporter"));
        NEW_TRANSPORTER = Clojure.var("clojure.tools.deps.alpha.util.s3-transporter", "new-transporter");
    }

    private static final IFn NEW_TRANSPORTER;

    public void initService(ServiceLocator locator) {
    }

    public Transporter newInstance(RepositorySystemSession session, RemoteRepository repository) {
        return (Transporter) NEW_TRANSPORTER.invoke(session, repository);
    }

    public float getPriority() {
        return 5.0f;
    }

}
