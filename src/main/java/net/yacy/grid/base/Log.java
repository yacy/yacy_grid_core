/**
 *  Data
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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.spi.LoggingEvent;

public class Log {

    public static Logger logger;
    public static LogAppender logAppender;

    public static void init() {
        PatternLayout layout = new PatternLayout("%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %p %c %x - %m%n");
        logger = Logger.getRootLogger();
        logger.removeAllAppenders();
        logAppender = new LogAppender(layout, 100000);
        logger.addAppender(logAppender);
        ConsoleAppender ca = new ConsoleAppender(layout);
        ca.setImmediateFlush(false);
        logger.addAppender(ca);

        // check network situation
        try {
            Log.logger.info("Local Host Address: " + InetAddress.getLocalHost().getHostAddress());
        } catch (UnknownHostException e1) {
            e1.printStackTrace();
        }
    }

    public static void clearCaches() {
        // should i.e. be called in case of short memory status
        logAppender.clean(5000);
    }

    public static void close() {

    }

    public static class LogAppender extends AppenderSkeleton {

        private int maxlines;
        private ConcurrentLinkedQueue<String> lines;
        private AtomicInteger a;

        public LogAppender(Layout layout, int maxlines) {
            this.layout = layout;
            this.maxlines = maxlines;
            this.lines = new ConcurrentLinkedQueue<>();
            String line = layout.getHeader();
            if (line != null) this.lines.add(line);
            this.a = new AtomicInteger(0);
        }

        @Override
        public void append(LoggingEvent event) {
            if (event == null) return;
            String line = this.layout.format(event);
            if (line != null) this.lines.add(line);
            if (event.getThrowableInformation() != null) {
                for (String t: event.getThrowableStrRep()) if (t != null)  this.lines.add(t + "\n");
            }
            if (this.a.incrementAndGet() % 100 == 0) {
                clean(this.maxlines);
                this.a.set(0);
            }
        }

        @Override
        public void close() {
            lines.clear();
            lines = null;
        }

        @Override
        public boolean requiresLayout() {
            return true;
        }

        public ArrayList<String> getLines(int max) {
            Object[] a = this.lines.toArray();
            ArrayList<String> l = new ArrayList<>();
            int start = Math.max(0, a.length - max);
            for (int i = start; i < a.length; i++) l.add((String) a[i]);
            return l;
        }

        public void clean(int remaining) {
            int c = this.lines.size() - remaining;
            while (c-- > 0) this.lines.poll();
        }

    }
}
