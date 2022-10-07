package io.digiexpress.spring.composer.controllers.util;



import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;


@Slf4j
public class ControllerUtil {
  private static final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

  public static IdeOnClasspath ideOnClasspath(String configContextPath) {
    final String contextPath;
    try {
      contextPath = FileUtils.cleanPath(configContextPath).length() <= 1 ? "/"
          : "/" + FileUtils.cleanPath(configContextPath) + "/";
    } catch (Exception e) {
      return new IdeOnClasspath();
    }

    try {
      final String path = "webjars/digiexpress-composer-ui/" + getVersion() + "/";

      final String js = chunkJs();
      final String hash = js.substring(0, js.length() - 3);
      final String manifest = resolveRuntimeScript("classpath*:**/digiexpress-composer-ui/**/manifest.json");

      final List<String> css = Arrays.asList(contextPath + path + "static/css/"
          + resolveRuntimeScript("classpath*:**/digiexpress-composer-ui/**/static/css/main*.css"));

      final IdeOnClasspath config = new IdeOnClasspath(
          hash, css, 
          contextPath + path + manifest,
          contextPath + path + "static/js/" + js);

      if (log.isDebugEnabled()) {
        log.debug("Digiexpress IDE is enabled." + System.lineSeparator() + config);
      }

      return config;
    } catch (Exception e) {
      log.debug("Digiexpress IDE is disabled.");
      return new IdeOnClasspath();
    }
  }

  private static String getVersion() throws IOException {
    Resource[] resources = resolver.getResources("classpath*:**/digiexpress-composer-ui/**/index.html");
    if (resources.length > 0) {
      String uri = resources[0].getURL().toString();
      String[] paths = uri.split("\\/");
      return paths[paths.length - 2];
    }
    return null;
  }

  private static String resolveRuntimeScript(String ideManifestJsPattern) throws IOException {
    Resource[] resources = resolver.getResources(ideManifestJsPattern);
    if (resources.length > 0) {
      return resources[0].getFilename();
    }
    return null;
  }

  private static String chunkJs() throws IOException {
    Resource[] resources = resolver.getResources("classpath*:**/digiexpress-composer-ui/**/main*.js");
    for (Resource resource : resources) {
      return resource.getFilename();
    }
    return null;
  }

  private static String getContextPath(String serverContextPath) {
    String cp = "";
    if (StringUtils.hasText(serverContextPath)) {
      if (!serverContextPath.startsWith("/")) {
        cp = "/";
      }
      cp = cp + serverContextPath;
      if (cp.endsWith("/")) {
        cp = cp.substring(0, cp.length() - 1);
      }
    }
    return cp;
  }

  private static String getUrl(String proto, String host, String serverContextPath) {
    final String contextPath = getContextPath(serverContextPath);
    if (!StringUtils.hasText(proto)) {
      proto = "http";
    }
    if (!proto.endsWith(":")) {
      proto = proto + ":";
    }
    String baseUrl = proto + "//" + host + contextPath;
    return baseUrl;
  }

  public static String getRestUrl(String proto, String host, String apiContextPath, String serverContextPath) {
    return FileUtils.cleanPath(getUrl(proto, host, serverContextPath)) + "/" + FileUtils.cleanPath(apiContextPath) + "/";
  }
}
