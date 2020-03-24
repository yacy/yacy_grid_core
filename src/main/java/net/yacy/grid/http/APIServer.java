/**
 *  APIServer
 *  Copyright 14.01.2017 by Michael Peter Christen, @0rb1t3r
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.grid.http;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.Servlet;

import org.apache.log4j.BasicConfigurator;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import net.yacy.grid.tools.MapUtil;

/**
 * main server class as static class: we made this static on purpose because then it is always
 * known which class is the holder of the server class - no-one. That makes it possible to address
 * the server methods from everywhere in the code, even if we do not know which other classes may
 * open a server. We also never want to open more than one server port at once.
 */
public class APIServer {

    private static List<Class<? extends Servlet>> services = new ArrayList<>();
    private static Map<String, APIHandler> serviceMap = new ConcurrentHashMap<>();
    private static Server server = null;

    public static File conf_dir;
    public static File data_dir;
    private static String service_name = "";
    private static int service_port = 8888;

    public static void initEnvironment(
            String name,
            int port,
            final List<Class<? extends Servlet>> services,
            final String data_path,  // this is usually "data"
            final boolean localStorage) {
        service_name = name;
        service_port = port;

        // run in headless mode
        System.setProperty("java.awt.headless", "true"); // no awt used here so we can switch off that stuff

        // configure logging
        BasicConfigurator.configure();

        // load the config file(s);
        // what we are doing here is a bootstraping of configuration file(s): first we load the system configuration
        // then we know the port for the service. As every server for a specific port may have its own configuration
        // file we need to load the configuration again.
        conf_dir = FileSystems.getDefault().getPath("conf").toFile();
        data_dir = FileSystems.getDefault().getPath(data_path).toFile();
        Map<String, String> config = readDoubleConfig("config.properties");

        // overwrite the config with environment variables. Because a '.' (dot) is not allowed in system environments
        // the dot can be replaced by "_" (underscore), i.e. like:
        // grid_broker_address="anonymous:yacy@127.0.0.1:5672" java -jar build/libs/yacy_grid_mcp-0.0.1-SNAPSHOT.jar
        String[] keys = config.keySet().toArray(new String[config.size()]); // create a clone of the keys to prevent a ConcurrentModificationException
        for (String key: keys) if (System.getenv().containsKey(key.replace('.', '_'))) config.put(key, System.getenv().get(key.replace('.', '_')));

        // the config can further be overwritten by System Properties, i.e. like:
        // java -jar -Dgrid.broker.address="anonymous:yacy@127.0.0.1:5672" build/libs/yacy_grid_mcp-0.0.1-SNAPSHOT.jar
        for (String key: keys) if (System.getProperties().containsKey(key)) config.put(key, System.getProperties().getProperty(key));

        // define services
        services.forEach(service -> APIServer.addService(service));

        // find data path
        service_port = Integer.parseInt(config.get("port"));
        Log.init();
    }

    /**
     * read the configuration two times: first to determine the port
     * and the second time to get the configuration from that specific port-related configuration
     * @param confFileName
     * @return
     */
    public static Map<String, String> readDoubleConfig(String confFileName) {
        File user_dir = new File(dataInstancePath(data_dir, service_port) , "conf");
        Map<String, String> config = MapUtil.readConfig(conf_dir, user_dir, confFileName);

        // read the port again and then read also the configuration again because the path of the custom settings may have moved
        if (config.containsKey("port")) {
            int port = Integer.parseInt(config.get("port"));
            user_dir = new File(dataInstancePath(data_dir, port) , "conf");
            config = MapUtil.readConfig(conf_dir, user_dir, confFileName);
        }
        return config;
    }

    private static File dataInstancePath(File data_dir, int port) {
        return new File(data_dir, service_name + "-" + port);
    }

    public static int getPort() {
        return service_port;
    }

    public static String getName() {
        return service_name;
    }

    public static void runService(final String html_path) {

        // give positive feedback
        Log.logger.info("Service started at port " + service_port);

        // prepare shutdown signal
        boolean pidkillfileCreated = false;
        // we use two files: one kill file which can be used to stop the process and one pid file which exists until the process runs
        // in case that the deletion of the kill file does not cause a termination, still a "fuser -k" on the pid file can be used to
        // terminate the process.
        File pidfile = new File(data_dir, service_name + "-" + service_port + ".pid");
        File killfile = new File(data_dir, service_name + "-" + service_port + ".kill");
        if (pidfile.exists()) pidfile.delete();
        if (killfile.exists()) killfile.delete();
        if (!pidfile.exists()) try {
            pidfile.createNewFile();
            if (pidfile.exists()) {pidfile.deleteOnExit(); pidkillfileCreated = true;}
        } catch (IOException e) {
            Log.logger.info("pid file " + pidfile.getAbsolutePath() + " creation failed: " + e.getMessage());
        }
        if (!killfile.exists()) try {
            killfile.createNewFile();
            if (killfile.exists()) killfile.deleteOnExit(); else pidkillfileCreated = false;
        } catch (IOException e) {
            Log.logger.info("kill file " + killfile.getAbsolutePath() + " creation failed: " + e.getMessage());
            pidkillfileCreated = false;
        }

        // wait for shutdown signal (kill on process)
        if (pidkillfileCreated) {
            // we can control this by deletion of the kill file
            Log.logger.info("to stop this process, delete kill file " + killfile.getAbsolutePath());
            while (APIServer.isAlive() && killfile.exists()) {
                try {Thread.sleep(1000);} catch (InterruptedException e) {}
            }
            APIServer.stop();
        } else {
            // something with the pid file creation did not work; fail-over to normal operation waiting for a kill command
            APIServer.join();
        }
        Log.logger.info("server nominal termination requested");
        Log.close();
        Log.logger.info("server terminated.");
    }

    public static int getServerThreads() {
        return server.getThreadPool().getThreads() - server.getThreadPool().getIdleThreads();
    }

    public static void addService(Class<? extends Servlet> service) {
        try {
            APIHandler handler = (APIHandler) service.newInstance();
            services.add(service);
            serviceMap.put(handler.getAPIName(), handler);
        } catch (InstantiationException | IllegalAccessException e) {
            Log.logger.warn("", e);
        }
    }

    public static APIHandler getAPI(String name) {
        return serviceMap.get(name);
    }

    public static int open(String htmlPath, boolean force) throws IOException {
        int ap = 0;
        while (true) {
            try {
                open(service_port + ap, htmlPath);
                return service_port + ap;
            } catch (IOException e) {
                if (force || ap >= 16) {
                    Log.logger.warn("", e);
                    throw e;
                }
                ap++;
                continue;
            }
        }
    }

    private static void open(int port, String htmlPath) throws IOException {
        try {
            QueuedThreadPool pool = new QueuedThreadPool();
            pool.setMaxThreads(100);
            server = new Server(pool);

            ServerConnector connector = new ServerConnector(server);
            HttpConfiguration http_config = new HttpConfiguration();
            http_config.setRequestHeaderSize(65536);
            http_config.setResponseHeaderSize(65536);
            connector.addConnectionFactory(new HttpConnectionFactory(http_config));
            connector.setPort(port);
            connector.setName("httpd:" + port);
            connector.setIdleTimeout(20000); // timout in ms when no bytes send / received
            server.addConnector(connector);
            server.setStopAtShutdown(true);

            // add services
            ServletContextHandler servletHandler = new ServletContextHandler();
            for (Class<? extends Servlet> service: services)
                try {
                    servletHandler.addServlet(service, ((APIHandler) (service.getConstructor().newInstance())).getAPIPath());
                } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                    Log.logger.warn(service.getName() + " instantiation error", e);
                }

            ErrorHandler errorHandler = new ErrorHandler();
            errorHandler.setShowStacks(true);
            servletHandler.setErrorHandler(errorHandler);

            HandlerList handlerlist2 = new HandlerList();
            if (htmlPath == null) {
                handlerlist2.setHandlers(new Handler[]{servletHandler, new DefaultHandler()});
            } else {
                FileHandler fileHandler = new FileHandler();
                fileHandler.setDirectoriesListed(true);
                fileHandler.setWelcomeFiles(new String[]{"index.html"});
                fileHandler.setResourceBase(htmlPath);
                handlerlist2.setHandlers(new Handler[]{fileHandler, servletHandler, new DefaultHandler()});
            }
            server.setHandler(handlerlist2);
            server.start();
        } catch (Throwable e) {
            throw new IOException(e.getMessage());
        }
    }

    public static boolean isAlive() {
        return server.isRunning() || server.isStarted() || server.isStarting();
    }

    public static void stop() {
        try {
            server.stop();
            server.destroy();
        } catch (Exception e) {
            Log.logger.warn("", e);
        }
    }

    public static void join() {
        try {
            server.getThreadPool().join();
        } catch (Exception e) {
            Log.logger.warn("", e);
        }
    }

}
