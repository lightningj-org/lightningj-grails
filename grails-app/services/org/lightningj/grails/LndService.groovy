/*
 *************************************************************************
 *                                                                       *
 *  LightningJ                                                           *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public License   *
 *  (LGPL-3.0-or-later)                                                  *
 *  License as published by the Free Software Foundation; either         *
 *  version 3 of the License, or any later version.                      *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.lightningj.grails

import grails.core.GrailsApplication
import io.grpc.ManagedChannel
import io.grpc.netty.GrpcSslContexts
import io.grpc.netty.NettyChannelBuilder
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslProvider
import org.grails.core.exceptions.GrailsConfigurationException
import org.lightningj.lnd.wrapper.AsynchronousLndAPI
import org.lightningj.lnd.wrapper.AsynchronousWalletUnlockerAPI
import org.lightningj.lnd.wrapper.MacaroonClientInterceptor
import org.lightningj.lnd.wrapper.MacaroonContext
import org.lightningj.lnd.wrapper.StaticFileMacaroonContext
import org.lightningj.lnd.wrapper.SynchronousLndAPI
import org.lightningj.lnd.wrapper.SynchronousWalletUnlockerAPI
import org.slf4j.Logger
import org.slf4j.LoggerFactory


import javax.annotation.PreDestroy

/**
 *  LND Service is a Singleton Service containing the Managed GRPC Channel towards
 *  the LND Daemon.
 *
 *  It can either be configured in application.yml (for basic settings)  or by using the init() method
 *  in Bootstrap (before any GRPC calls).
 */

class LndService {

    // TODO Test Shutdown
    // TODO TEST MultiThreading
    GrailsApplication grailsApplication
    Logger log = LoggerFactory.getLogger(LndService)


    private SynchronousLndAPI syncAPI = null
    private AsynchronousLndAPI asyncAPI = null
    private ManagedChannel managedChannel = null
    private Wallet wallet = new Wallet()

    class Wallet{
        SynchronousWalletUnlockerAPI walletUnlockSyncAPI = null
        AsynchronousWalletUnlockerAPI walletUnlockAsyncAPI = null

        /**
         * Retrieves the Wallet Unlock Synchronous (Blocking) API instance.
         * @return the Wallet Unlock Synchronous (Blocking) API instance.
         */
        SynchronousWalletUnlockerAPI getSync() {
            if(walletUnlockSyncAPI == null){
                walletUnlockSyncAPI = new SynchronousWalletUnlockerAPI(getChannel())
            }
            return walletUnlockSyncAPI
        }

        /**
         * Retrieves the Wallet Unlock Asynchronous (Non-blocking) API instance.
         * @return the Wallet Unlock Asynchronous (Non-blocking) API instance.
         */
        AsynchronousWalletUnlockerAPI getAsync(){
            if(walletUnlockAsyncAPI == null){
                walletUnlockAsyncAPI = new AsynchronousWalletUnlockerAPI(getChannel())
            }
            return walletUnlockAsyncAPI
        }
    }

    /**
     * Getter for the wallet APIs
     * @return all wallet unlock apis.
     */
    Wallet getWallet(){
        return wallet
    }

    /**
     * Method to manually init the Service with a managed channel with custom SSL Context or Macaroon Context.
     *
     * @param managedChannel the managed channel to use to communicate with LND.
     */
    void init(ManagedChannel managedChannel){
        log.debug("Initializing LndService manually.")
        this.managedChannel = managedChannel
    }

    /**
     * Retrieves the LND Synchronous (Blocking) API instance.
     * @return the LND Synchronous (Blocking) API instance.
     */
    SynchronousLndAPI getSync() {
        if(syncAPI == null){
            syncAPI = new SynchronousLndAPI(getChannel())
        }
        return syncAPI
    }

    /**
     * Retrieves the LND Asynchronous (Non-blocking) API instance.
     * @return the LND Asynchronous (Non-blocking) API instance.
     */
    AsynchronousLndAPI getAsync(){
        if(asyncAPI == null){
            asyncAPI = new AsynchronousLndAPI(getChannel())
        }
        return asyncAPI
    }

    /**
     * Method to release all underlying resources. Is called automatically during shutdown of
     * the application.
     */
    @PreDestroy
    void close(){
        log.debug("Shutting down LND API Channels.")
        if(syncAPI){
            syncAPI.close()
            syncAPI = null
        }
        if(asyncAPI){
            asyncAPI.close()
            asyncAPI = null
        }
        if(wallet.walletUnlockSyncAPI){
            wallet.walletUnlockSyncAPI.close()
        }

        if(wallet.walletUnlockAsyncAPI){
            wallet.walletUnlockAsyncAPI.close()
        }
    }

    /**
     * Method creating a simple managed channel from the application.yml configuration.
     *
     * Requires the following settings:
     * <li>lightningj.lnd.host: the hostname of the LND GRPC connection.
     * <li>lightningj.lnd.port: the port of the LND GRPC connection.
     * <li>lightningj.lnd.trustedcert: The path to the trusted certificate in PEM format.
     * <li>lightningj.lnd.macaroon: The path to the macaroon file used to authenticate towards LND.
     *
     * @return the managed channel to communicate with LND process.
     * @throws GrailsConfigurationException if application.yml is missconfigured.
     */
    private ManagedChannel getChannel() throws GrailsConfigurationException{
        if(managedChannel == null){

            String host = grailsApplication.config.getProperty("lightningj.lnd.host")
            if(!host){
                throw new GrailsConfigurationException("Error in lightningj configuration, required setting 'lightningj.lnd.host' not found.")
            }
            int port
            try {
                port = Integer.parseInt(grailsApplication.config.getProperty("lightningj.lnd.port"))
            }catch(Exception e){
                throw new GrailsConfigurationException("Error in lightningj configuration, required setting 'lightningj.lnd.port' not found or not number.")
            }


            String trustedcertPath = grailsApplication.config.getProperty("lightningj.lnd.trustedcert")
            if(!trustedcertPath){
                throw new GrailsConfigurationException("Error in lightningj configuration, required setting 'lightningj.lnd.trustedcert' not found.")
            }

            File trustFile = getFileSetting("lightningj.lnd.trustedcert", true)
            File macaroonFile = getFileSetting("lightningj.lnd.macaroon", false)

            SslContext sslContext = GrpcSslContexts.configure(SslContextBuilder.forClient(), SslProvider.OPENSSL)
                    .trustManager(trustFile)
                    .build()

            MacaroonContext macaroonContext = macaroonFile != null ? new StaticFileMacaroonContext(macaroonFile) : null

            if(!macaroonContext){
                log.debug("No LND Macaroon file specified, make sure you run your LND node without macaroon requirement.")
            }
            log.debug("Setting up LND GPRC Channel with ${host}:${port}.")
            managedChannel = NettyChannelBuilder.forAddress(host, port)
                    .sslContext(sslContext)
                    .intercept(new MacaroonClientInterceptor(macaroonContext))
                    .build()
        }
        return managedChannel
    }

    /**
     * Help method to read a setting pointing to a file in the file system.
     *
     * @param setting the setting to fetch from application.yml
     * @param required the exception should be thrown if setting doesn't exist.
     * @return the File pointed to by the path.
     * @throws GrailsConfigurationException if setting is missing or file doesn't exists or isn't readable.
     */
    private File getFileSetting(String setting, boolean required) throws GrailsConfigurationException{
        String filePath = grailsApplication.config.getProperty(setting)

        if (!filePath && required) {
            throw new GrailsConfigurationException("Error in lightningj configuration, required setting ${setting} not found.")
        }

        if(!filePath){
            return null
        }

        File file = new File(filePath)
        if(!file.exists() || !file.canRead() || !file.isFile()){
            throw new GrailsConfigurationException("Error reading file from setting in lightningj configuration, check that ${setting} that points to ${filePath} an existing readable file.")
        }

        return file
    }



}
