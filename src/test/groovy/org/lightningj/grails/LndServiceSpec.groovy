package org.lightningj.grails


import grails.testing.services.ServiceUnitTest
import io.grpc.ManagedChannel
import org.grails.config.PropertySourcesConfig
import org.grails.config.yaml.YamlPropertySourceLoader
import org.grails.core.exceptions.GrailsConfigurationException
import org.lightningj.lnd.wrapper.AsynchronousLndAPI
import org.lightningj.lnd.wrapper.AsynchronousWalletUnlockerAPI
import org.lightningj.lnd.wrapper.SynchronousLndAPI
import org.lightningj.lnd.wrapper.SynchronousWalletUnlockerAPI
import org.slf4j.Logger
import org.springframework.core.io.ByteArrayResource
import spock.lang.Specification

class LndServiceSpec extends Specification implements ServiceUnitTest<LndService>{

    String tlsCertPath = new File(this.getClass().getResource("/tls.cert").path)
    String adminMacaroonPath =new File(this.getClass().getResource("/admin.macaroon").path)

    def setup() {
        service.log = Mock(Logger)
    }

    void "Verify that init sets the ManagedChannel"(){
        expect:
        service.managedChannel == null
        when:
        service.init(Mock(ManagedChannel))
        then:
        service.managedChannel != null
        service.log.debug("Initializing LndService manually.")
    }

    void "Verify that getSync() is lazy-loading and caching the API"(){
        setup:
        useConfig(validConfig)
        expect:
        service.syncAPI == null
        when:
        def api = service.sync
        then:
        api instanceof SynchronousLndAPI
        service.sync == api
    }

    void "Verify that getAsync() is lazy-loading and caching the API"(){
        setup:
        useConfig(validConfig)
        expect:
        service.asyncAPI == null
        when:
        def api = service.async
        then:
        api instanceof AsynchronousLndAPI
        service.async == api
    }

    void "Verify that wallet.getSync() is lazy-loading and caching the API"(){
        setup:
        useConfig(validConfig)
        expect:
        service.wallet.walletUnlockSyncAPI == null
        when:
        def api = service.wallet.sync
        then:
        api instanceof SynchronousWalletUnlockerAPI
        service.wallet.sync == api
    }

    void "Verify that wallet.getAsync() is lazy-loading and caching the API"(){
        setup:
        useConfig(validConfig)
        expect:
        service.wallet.walletUnlockAsyncAPI == null
        when:
        def api = service.wallet.async
        then:
        api instanceof AsynchronousWalletUnlockerAPI
        service.wallet.async == api
    }

    void "Verify that close calls close on all underlying APIs"(){
        setup:
        def syncAPI = Mock(SynchronousLndAPI)
        def asyncAPI = Mock(AsynchronousLndAPI)
        service.syncAPI = syncAPI
        service.asyncAPI = asyncAPI
        when:
        service.close()
        then:
        service.syncAPI == null
        service.asyncAPI == null
        1 * syncAPI.close()
        1 * asyncAPI.close()
        1 * service.log.debug("Shutting down LND API Channels.")
    }

    void "Verify that getManagedChannel() with a valid configuration returns and caches a managed channel."() {
        setup:
        useConfig(validConfig)
        expect:"Verify correct configuration is used"
        grailsApplication.config.getProperty('lightningj.lnd.host') == "localhost"
        service.managedChannel == null
        when:
        def channel = service.getChannel()
        then:
        channel instanceof ManagedChannel
        service.managedChannel == channel
        1 * service.log.debug("Setting up LND GPRC Channel with localhost:10001.")
    }

    void "Verify that getManagedChannel() throws GrailsConfigurationException if no host is configured"(){
        setup:
        useConfig(noHostConfig)
        when:
        service.getChannel()
        then:
        def e = thrown GrailsConfigurationException
        e.message == "Error in lightningj configuration, required setting 'lightningj.lnd.host' not found."
    }

    void "Verify that getManagedChannel() throws GrailsConfigurationException if no port is configured"(){
        setup:
        useConfig(noPortConfig)
        when:
        service.getChannel()
        then:
        def e = thrown GrailsConfigurationException
        e.message == "Error in lightningj configuration, required setting 'lightningj.lnd.port' not found or not number."
    }

    void "Verify that getManagedChannel() throws GrailsConfigurationException if port is not a number."(){
        setup:
        useConfig(invalidPortConfig)
        when:
        service.getChannel()
        then:
        def e = thrown GrailsConfigurationException
        e.message == "Error in lightningj configuration, required setting 'lightningj.lnd.port' not found or not number."
    }

    void "Verify that getManagedChannel() throws GrailsConfigurationException if no trust cert is configured"(){
        setup:
        useConfig(noTrustedCertConfig)
        when:
        service.getChannel()
        then:
        def e = thrown GrailsConfigurationException
        e.message == "Error in lightningj configuration, required setting 'lightningj.lnd.trustedcert' not found."
    }

    void "Verify that getManagedChannel() throws GrailsConfigurationException if invalid path to trust cert is configured"(){
        setup:
        useConfig(invalidCertTrustCertPathConfig)
        when:
        service.getChannel()
        then:
        def e = thrown GrailsConfigurationException
        e.message == "Error reading file from setting in lightningj configuration, check that lightningj.lnd.trustedcert that points to invalid/path/tls.cert an existing readable file."
    }


    void "Verify that getManagedChannel() throws GrailsConfigurationException if invalid path to macaroon is configured"(){
        setup:
        useConfig(invalidMacaroonPathConfig)
        when:
        service.getChannel()
        then:
        def e = thrown GrailsConfigurationException
        e.message == "Error reading file from setting in lightningj configuration, check that lightningj.lnd.macaroon that points to invalid/path/admin.macaroon an existing readable file."
    }

    void "Verify that getManagedChannel() performs debug logging if no macaroon path is configured"(){
        setup:
        useConfig(noMacaroonConfig)
        when:
        def channel = service.getChannel()
        then:
        channel instanceof ManagedChannel
        1 * service.log.debug("No LND Macaroon file specified, make sure you run your LND node without macaroon requirement.")
    }

    private void useConfig(String config){
        def resource = new ByteArrayResource(config.getBytes("UTF-8"))
        def mapPropertySource = new YamlPropertySourceLoader().load(
                "test.yml", resource, null)
        grailsApplication.config = new PropertySourcesConfig(mapPropertySource.getSource())

    }

    def validConfig = """
lightningj:
    lnd:
        host: localhost
        port: 10001
        trustedcert: ${tlsCertPath}
        macaroon: ${adminMacaroonPath}

"""

    def noHostConfig = """
lightningj:
    lnd:
        port: 10001
        trustedcert: ${tlsCertPath}
        macaroon: ${adminMacaroonPath}

"""

    def noPortConfig = """
lightningj:
    lnd:
        host: localhost
        trustedcert: ${tlsCertPath}
        macaroon: ${adminMacaroonPath}

"""

    def invalidPortConfig = """
lightningj:
    lnd:
        host: localhost
        port: abc10001
        trustedcert: ${tlsCertPath}
        macaroon: ${adminMacaroonPath}

"""

    def noTrustedCertConfig = """
lightningj:
    lnd:
        host: localhost
        port: 10001
        macaroon: ${adminMacaroonPath}

"""
    def invalidCertTrustCertPathConfig = """
lightningj:
    lnd:
        host: localhost
        port: 10001
        trustedcert: invalid/path/tls.cert
        macaroon: ${adminMacaroonPath}

"""

    def noMacaroonConfig = """
lightningj:
    lnd:
        host: localhost
        port: 10001
        trustedcert: ${tlsCertPath}

"""

    def invalidMacaroonPathConfig = """
lightningj:
    lnd:
        host: localhost
        port: 10001
        trustedcert: ${tlsCertPath}
        macaroon: invalid/path/admin.macaroon

"""
}
