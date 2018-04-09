package org.lightningj.grails

import grails.plugins.*

class LightningjGrailsGrailsPlugin extends Plugin {

    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "3.3.0 > *"
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
        "grails-app/views/error.gsp"
    ]

    def title = "Lightningj Grails" // Headline display name of the plugin
    def author = "herrvendil"
    def authorEmail = "info@lightningj.org"
    def description = '''\
Grails 3.x Plugin to integrate LightningJ into a Grails application.
'''

    // URL to the plugin's documentation
    def documentation = "http://grails.lightningj.org"

    // Extra (optional) plugin metadata

    // License: one of 'APACHE', 'GPL2', 'GPL3'
    def license = "LGPL3"

    // Details of company behind the plugin (if there is one)
    def organization = [ name: "LightningJ", url: "http://lightningj.org/" ]

    // Any additional developers beyond the author specified above.
    def developers = [ [ name: "Philip Vendil", email: "info@lightningj.org" ]]

    // Location of the plugin's issue tracker.
    def issueManagement = [ system: "Github Issues", url: "https://github.com/lightningj-org/lightningj-grails/issues" ]

    // Online location of the plugin's browseable source code.
    def scm = [ url: "https://github.com/lightningj-org/lightningj-grails" ]

    Closure doWithSpring() {


    }

    void doWithDynamicMethods() {
        // TODO Implement registering dynamic methods to classes (optional)
    }

    void doWithApplicationContext() {
        // TODO Implement post initialization spring config (optional)
    }

    void onChange(Map<String, Object> event) {
        // TODO Implement code that is executed when any artefact that this plugin is
        // watching is modified and reloaded. The event contains: event.source,
        // event.application, event.manager, event.ctx, and event.plugin.
    }

    void onConfigChange(Map<String, Object> event) {
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.
    }

    void onShutdown(Map<String, Object> event) {
        // TODO Implement code that is executed when the application shuts down (optional)
    }
}
