package org.jglrxavpok.tinyjukebox.templating

import freemarker.cache.URLTemplateLoader
import freemarker.template.Configuration
import freemarker.template.TemplateExceptionHandler
import java.io.Writer
import java.net.URL

object FreeMarker {
    private lateinit var cfg: Configuration

    fun init() {
        cfg = Configuration(Configuration.VERSION_2_3_27)
        cfg.outputEncoding = "UTF-8"
        cfg.defaultEncoding = "UTF-8"
        cfg.templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
        cfg.logTemplateExceptions = false
        cfg.wrapUncheckedExceptions = true
        cfg.templateLoader = object: URLTemplateLoader() {
            override fun getURL(name: String?): URL? {
                println(">> $name / ${FreeMarker::class.java.getResource("/$name")}")
                return FreeMarker::class.java.getResource("/$name")
            }
        }
    }

    fun processTemplate(path: String, dataModel: Any, writer: Writer) {
        cfg.getTemplate(path).process(dataModel, writer)
    }
}
