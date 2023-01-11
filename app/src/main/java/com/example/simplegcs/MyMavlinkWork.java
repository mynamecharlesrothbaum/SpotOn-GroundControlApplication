package com.example.simplegcs;

import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.Date;

import io.dronefleet.mavlink.Mavlink2Message;
import io.dronefleet.mavlink.MavlinkConnection;
import io.dronefleet.mavlink.MavlinkMessage;
import io.dronefleet.mavlink.common.CommandLong;
import io.dronefleet.mavlink.common.GpsFixType;
import io.dronefleet.mavlink.common.GpsRawInt;
import io.dronefleet.mavlink.common.Heartbeat;
import io.dronefleet.mavlink.common.MavAutopilot;
import io.dronefleet.mavlink.common.MavCmd;
import io.dronefleet.mavlink.common.MavMode;
import io.dronefleet.mavlink.common.MavModeFlag;
import io.dronefleet.mavlink.common.MavType;
import io.dronefleet.mavlink.common.SetMode;
import io.dronefleet.mavlink.common.Statustext;
import io.dronefleet.mavlink.common.SysStatus;

public class MyMavlinkWork implements Runnable {
    MavlinkConnection mav_conn;
    Handler ui_handler;
    static String[] FLIGHT_MODE = {"STABILIZE", "ACRO", "ALT_HOLD", "AUTO", "GUIDED", "LOITER", "RTL", "CIRCLE", "POSITION", "LAND"};
    static String[] GPS_FIX_TYPE = {"No GPS", "No Fix", "2D Fix", "3D Fix", "DGPS", "RTK Float", "RTK Fix"};
    long last_sys_status_ts = 0;
    long last_gps_raw_ts = 0;
    long last_hb_ts = 0;
    Runnable chk_disconn = new Runnable() {
        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(5000);
                    if (last_hb_ts > 0 && (SystemClock.elapsedRealtime() - last_hb_ts > 3000)) {
                        Message ui_msg = ui_handler.obtainMessage(2, "vehicle disconnected " + DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date()));
                        ui_handler.sendMessage(ui_msg);
                        last_hb_ts = 0;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    public MyMavlinkWork(Handler handler, InputStream is, OutputStream os) {
        mav_conn = MavlinkConnection.create(is, os);
        ui_handler = handler;
        Thread t1 = new Thread(chk_disconn);
        t1.start();
    }

    public void setModeLand() {
        try {
            mav_conn.send1(255,0, SetMode.builder().baseMode(MavModeFlag.MAV_MODE_FLAG_CUSTOM_MODE_ENABLED).customMode(9).build());
        } catch (IOException e) {
            Log.d("chobits", e.getMessage());
        }
    }

    public void setModeRTL() {
        try {
            mav_conn.send1(255,0, SetMode.builder().baseMode(MavModeFlag.MAV_MODE_FLAG_CUSTOM_MODE_ENABLED).customMode(6).build());
        } catch (IOException e) {
            Log.d("chobits", e.getMessage());
        }
    }

    @Override
    public void run() {
        MavlinkMessage msg;
        while (true) {
            try {
                msg = mav_conn.next();
            } catch (IOException e) {
                Log.d("chobits", e.getMessage());
                break;
            }
            if (msg == null) break;
            //if (msg instanceof Mavlink2Message) {
            //    Log.d("chobits", "mavlink 2 msg");
            //}
            Object msg_payload = msg.getPayload();
            if (msg_payload instanceof Heartbeat) {
                // This is a heartbeat message
                Heartbeat hb = (Heartbeat)msg_payload;
                Log.d("chobits", "heartbeat " + msg.getOriginSystemId() + "," + hb.customMode() + "," + msg.getSequence());
                Message ui_msg = ui_handler.obtainMessage(1, FLIGHT_MODE[(int)hb.customMode()]);
                ui_handler.sendMessage(ui_msg);

                if (last_hb_ts == 0) {
                    ui_msg = ui_handler.obtainMessage(2, "vehicle " + msg.getOriginSystemId() + " connected " + DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date()));
                    ui_handler.sendMessage(ui_msg);
                }
                last_hb_ts = SystemClock.elapsedRealtime();

                //send hb to fc, keeping link active, otherwise we will not rcv status_txt
                try {
                    mav_conn.send1(255,0,Heartbeat.builder().type(MavType.MAV_TYPE_GCS).autopilot(MavAutopilot.MAV_AUTOPILOT_INVALID).mavlinkVersion(3).build());
                } catch (IOException e) {
                    Log.d("chobits", e.getMessage());
                }

                long ts = SystemClock.elapsedRealtime();
                if (ts - last_sys_status_ts > 3000) {
                    try {
                        mav_conn.send1(255, 0, CommandLong.builder().command(MavCmd.MAV_CMD_SET_MESSAGE_INTERVAL).param1(1).param2(1000000).build());
                    } catch (IOException e) {
                        Log.d("chobits", e.getMessage());
                    }
                }
                if (ts - last_gps_raw_ts > 3000) {
                    try {
                        mav_conn.send1(255, 0, CommandLong.builder().command(MavCmd.MAV_CMD_SET_MESSAGE_INTERVAL).param1(24).param2(1000000).build());
                    } catch (IOException e) {
                        Log.d("chobits", e.getMessage());
                    }
                }
            } else if (msg_payload instanceof Statustext) {
                Statustext txt = (Statustext)msg_payload;
                Log.d("chobits", msg.getOriginSystemId() + "," + txt.text());
                Message ui_msg = ui_handler.obtainMessage(2, txt.text());
                //Bundle data = new Bundle();
                //data.putString("message", txt.text());
                //ui_msg.setData(data);
                ui_handler.sendMessage(ui_msg);
            } else if (msg_payload instanceof SysStatus) {
                last_sys_status_ts = SystemClock.elapsedRealtime();
                SysStatus status = (SysStatus)msg_payload;
                Message ui_msg = ui_handler.obtainMessage(3, status.voltageBattery(), status.currentBattery());
                ui_handler.sendMessage(ui_msg);
            } else if (msg_payload instanceof GpsRawInt) {
                last_gps_raw_ts = SystemClock.elapsedRealtime();
                GpsRawInt gps_raw = (GpsRawInt)msg_payload;
                String txt;
                if (gps_raw.fixType().value() > 1) {
                    txt = GPS_FIX_TYPE[gps_raw.fixType().value()] + " HDOP:" + String.format("%0.1f", gps_raw.eph() * 0.01) + " " + gps_raw.satellitesVisible() + " satellites";
                } else {
                    txt = GPS_FIX_TYPE[gps_raw.fixType().value()];
                }
                Message ui_msg = ui_handler.obtainMessage(4, txt);
                ui_handler.sendMessage(ui_msg);
            } else {
                //Log.d("chobits", msg.getPayload().getClass().getSimpleName());
            }
        }
    }
}
