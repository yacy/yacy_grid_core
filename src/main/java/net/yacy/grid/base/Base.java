/**
 *  Base
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

package net.yacy.grid.base;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.Servlet;

import net.yacy.grid.base.api.info.LogService;
import net.yacy.grid.base.api.info.StatusService;
import net.yacy.grid.base.api.info.ThreaddumpService;
import net.yacy.grid.http.APIServer;
import net.yacy.grid.http.Log;
import net.yacy.grid.tools.GitTool;

public class Base {

    public final static String DATA_PATH = "data";
 
    // define services
    @SuppressWarnings("unchecked")
    public final static Class<? extends Servlet>[] BASE_SERVICES = new Class[]{
            // information services
            StatusService.class,
            ThreaddumpService.class,
            LogService.class,
    };

    public static void main(String[] args) {
        // initialize environment variables
        System.setProperty("java.awt.headless", "true"); // no awt used here so we can switch off that stuff

        // start server
        List<Class<? extends Servlet>> services = new ArrayList<>();
        services.addAll(Arrays.asList(BASE_SERVICES));
        APIServer.initEnvironment("base", 8888, services, DATA_PATH, true);
        try {
            APIServer.open(null, false);

            // start server
            Log.logger.info("started Base!");
            Log.logger.info("read status of base peer:");
            Log.logger.info("curl http://127.0.0.1:8010/yacy/grid/base/info/status.json");
            Log.logger.info("curl http://127.0.0.1:8010/yacy/grid/base/info/log.txt");
            Log.logger.info("curl http://127.0.0.1:8010/yacy/grid/base/info/threaddump.txt");
            Log.logger.info(new GitTool().toString());
            APIServer.runService(null);
        } catch (IOException e) {
            Log.logger.info("cannot start Base");
            e.printStackTrace();
        }

    }

}
