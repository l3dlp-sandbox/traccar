/*
 * Copyright 2016 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.protocol;

import java.net.SocketAddress;
import java.util.regex.Pattern;

import org.jboss.netty.channel.Channel;
import org.traccar.BaseProtocolDecoder;
import org.traccar.DeviceSession;
import org.traccar.Protocol;
import org.traccar.helper.DateBuilder;
import org.traccar.helper.Parser;
import org.traccar.helper.Parser.CoordinateFormat;
import org.traccar.helper.PatternBuilder;
import org.traccar.model.Position;

public class IdplProtocolDecoder extends BaseProtocolDecoder {

    public IdplProtocolDecoder(Protocol protocol) {
        super(protocol);
    }

    private static final Pattern PATTERN = new PatternBuilder()
            .text("*ID")                         // start of frame
            .number("(d+),")                     // command code
            .number("(d+),")                     // imei
            .number("(dd)(dd)(dd),")             // current date
            .number("(dd)(dd)(dd),")             // current time
            .expression("([A|V]),")              // gps fix
            .number("(dd)(dd).?(d+),([NS]),")    // latitude
            .number("(ddd)(dd).?(d+),([EW]),")   // longitude
            .number("(d{1,3}.dd),")              // speed
            .number("(d{1,3}.dd),")              // course
            .number("(d{1,2}),")                 // sats
            .number("(d{1,3}),")                 // gsm signal strength
            .expression("([A|N|S]),")            // vehicle status
            .expression("([0|1]),")              // main power status
            .number("(d.dd),")                   // internal battery voltage
            .expression("([0|1]),")              // sos alert
            .expression("([0|1]),")              // body tamper
            .expression("([0|1])([0|1]),")       // ac status + ign status
            .expression("([0|1|2]),")            // output1 status
            .number("(d{1,3}),")                 // adc1
            .number("(d{1,3}),")                 // adc2
            .expression("([0-9A-Z]{3}),")        // software version
            .expression("([L|R]),")              // message type
            .number("(x{4})#")                   // crc
            .compile();

    @Override
    protected Object decode(
            Channel channel, SocketAddress remoteAddress, Object msg) throws Exception {

        Parser parser = new Parser(PATTERN, (String) msg);
        if (!parser.matches()) {
            return null;
        }

        Position position = new Position();
        position.setProtocol(getProtocolName());

        position.set(Position.KEY_TYPE, parser.nextInt());

        DeviceSession deviceSession = getDeviceSession(channel, remoteAddress, parser.next());
        if (deviceSession == null) {
            return null;
        }
        position.setDeviceId(deviceSession.getDeviceId());

        DateBuilder dateBuilder = new DateBuilder()
                .setDateReverse(parser.nextInt(), parser.nextInt(), parser.nextInt())
                .setTime(parser.nextInt(), parser.nextInt(), parser.nextInt());
        position.setTime(dateBuilder.getDate());

        position.setValid(parser.next().equals("A"));
        position.setLatitude(parser.nextCoordinate(CoordinateFormat.DEG_MIN_MIN_HEM));
        position.setLongitude(parser.nextCoordinate(CoordinateFormat.DEG_MIN_MIN_HEM));
        position.setSpeed(parser.nextDouble());
        position.setCourse(parser.nextDouble());

        position.set(Position.KEY_SATELLITES, parser.nextInt());
        position.set(Position.KEY_RSSI, parser.nextInt());
        position.set("vehicleStatus", parser.next());
        position.set(Position.KEY_POWER, parser.nextInt());
        position.set(Position.KEY_BATTERY, parser.nextDouble());
        if (parser.nextInt() == 1) {
            position.set(Position.KEY_ALARM, Position.ALARM_SOS);
        }
        position.set("bodyTamper", parser.nextInt());
        position.set("acStatus", parser.nextInt());
        position.set(Position.KEY_IGNITION, parser.nextInt() == 1);
        position.set(Position.KEY_OUTPUT, parser.nextInt());
        position.set(Position.PREFIX_ADC + 1, parser.nextInt());
        position.set(Position.PREFIX_ADC + 2, parser.nextInt());
        position.set(Position.KEY_VERSION_FW, parser.next());
        position.set(Position.KEY_ARCHIVE, parser.next().equals("R"));

        parser.next(); // checksum

        return position;
    }

}
