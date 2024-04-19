package com.chobitsfan.minigcs;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.Date;

import io.dronefleet.mavlink.MavlinkConnection;
import io.dronefleet.mavlink.MavlinkMessage;
import io.dronefleet.mavlink.common.CommandLong;
import io.dronefleet.mavlink.common.GlobalPositionInt;
import io.dronefleet.mavlink.common.GpsRawInt;
import io.dronefleet.mavlink.common.Heartbeat;
import io.dronefleet.mavlink.common.MavAutopilot;
import io.dronefleet.mavlink.common.MavCmd;
import io.dronefleet.mavlink.common.MavFrame;
import io.dronefleet.mavlink.common.MavModeFlag;
import io.dronefleet.mavlink.common.MavParamType;
import io.dronefleet.mavlink.common.MavType;
import io.dronefleet.mavlink.common.MissionItemInt;
import io.dronefleet.mavlink.common.MissionItemReached;
import io.dronefleet.mavlink.common.ParamRequestRead;
import io.dronefleet.mavlink.common.ParamSet;
import io.dronefleet.mavlink.common.ParamValue;
import io.dronefleet.mavlink.common.SetMode;
import io.dronefleet.mavlink.common.Statustext;
import io.dronefleet.mavlink.common.SysStatus;

public class MyMavlinkWork implements Runnable {
    MavlinkConnection mav_conn;
    Handler ui_handler;
    Vehicle vehicle = Vehicle.getInstance(MavAutopilot.MAV_AUTOPILOT_ARDUPILOTMEGA, MavType.MAV_TYPE_QUADROTOR);
    static String[] GPS_FIX_TYPE = {"No GPS", "No Fix", "2D Fix", "3D Fix", "DGPS", "RTK Float", "RTK Fix"};
    public static final int UI_FLIGHT_MODE = 1;
    public static final int UI_STATUS_TXT = 2;
    public static final int UI_BAT_STATUS = 3;
    public static final int UI_GPS_STATUS = 4;
    public static final int UI_PARAM_VAL = 5;
    public static final int UI_GLOBAL_POS = 6;
    public static final int UI_AP_NAME = 7;
    long last_sys_status_ts = 0;
    long last_gps_raw_ts = 0;
    long last_global_pos_ts = 0;
    long last_hb_ts = 0;
    long param_rw_sent_ts = 0;
    int prv_flight_mode = -1;
    Runnable chk_disconn = new Runnable() {
        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(2000);
                    long ts = SystemClock.elapsedRealtime();
                    if (last_hb_ts > 0 && (ts - last_hb_ts > 3000)) {
                        last_hb_ts = 0;
                        Message ui_msg = ui_handler.obtainMessage(UI_STATUS_TXT, "vehicle disconnected " + DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date()));
                        ui_handler.sendMessage(ui_msg);
                    }
                    if (param_rw_sent_ts > 0 && (ts - param_rw_sent_ts > 3000)) {
                        param_rw_sent_ts = 0;
                        Message ui_msg = ui_handler.obtainMessage(UI_PARAM_VAL);
                        Bundle data = new Bundle();
                        data.putString("name", "chobits_param_rw_failed");
                        ui_msg.setData(data);
                        ui_handler.sendMessage(ui_msg);
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

    public void takeoff() {
        try {
            CommandLong takeoffCommand = CommandLong.builder()
                    .command(MavCmd.MAV_CMD_NAV_TAKEOFF)
                    .param7(3) // set to desired altitude in meters
                    .build();

            mav_conn.send1(255, 0, takeoffCommand);
        } catch (IOException e) {
            if (MyAppConfig.DEBUG) Log.d("chobits", "Failed to send takeoff command: " + e.getMessage());
        }
    }

    public void setModeGuided() {
        try {
            mav_conn.send1(255,0, vehicle.Guided());
        } catch (IOException e) {
            if (MyAppConfig.DEBUG) Log.d("chobits", "Failed to set mode to Guided: " + e.getMessage());
        }
    }
    public void setModeStabilize() {
        try {
            mav_conn.send1(255,0, vehicle.Stabilize());
        } catch (IOException e) {
            if (MyAppConfig.DEBUG) Log.d("chobits", "Failed to set mode to Stabilize: " + e.getMessage());
        }
    }

    public void sendLocalWaypoint(float lat, float lon, float alt) {
        try {
            MissionItemInt waypointCommand = MissionItemInt.builder()
                    .targetSystem(255) // System ID for the target system/vehicle
                    .targetComponent(0) // Component ID for the target component
                    .seq(0) // Sequence number of the waypoint
                    .frame(MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT_INT) // Use relative altitude
                    .command(MavCmd.MAV_CMD_NAV_WAYPOINT) // Command to navigate to a waypoint
                    .current(1) // Set to 1 to indicate this is the current waypoint
                    .autocontinue(1) // Set to 1 to allow auto-continuing to the next waypoint
                    .param1(0) // Hold time in seconds (ignored for simple waypoint navigation)
                    .param2(0) // Acceptance radius in meters (if the vehicle is within this distance of the waypoint, consider it reached)
                    .param3(0) // Pass-through waypoint (0 to fly directly to the waypoint)
                    .param4(0) // Desired yaw angle at waypoint (ignored unless YAW_ANGLE is used)
                    .x((int) (lat * 1E7)) // Latitude in decimal degrees * 1E7
                    .y((int) (lon * 1E7)) // Longitude in decimal degrees * 1E7
                    .z(alt) // Altitude in meters
                    .build();

            mav_conn.send1(255, 0, waypointCommand);
        } catch (IOException e) {
            if (MyAppConfig.DEBUG) Log.d("chobits", "Failed to send local waypoint: " + e.getMessage());
        }
    }



    public void setModeLand() {
        try {
            mav_conn.send1(255,0, vehicle.Land());
        } catch (IOException e) {
            if (MyAppConfig.DEBUG) Log.d("chobits", e.getMessage());
        }
    }

    public void readParam(String name) {
        param_rw_sent_ts = SystemClock.elapsedRealtime();
        try {
            mav_conn.send1(255,0, ParamRequestRead.builder().paramId(name.toUpperCase()).paramIndex(-1).build());
        } catch (IOException e) {
            if (MyAppConfig.DEBUG) Log.d("chobits", e.getMessage());
        }
    }

    public void writeParam(String name, float val) {
        param_rw_sent_ts = SystemClock.elapsedRealtime();
        try {
            mav_conn.send1(255,0, ParamSet.builder().paramId(name.toUpperCase()).paramValue(val).build());
        } catch (IOException e) {
            if (MyAppConfig.DEBUG) Log.d("chobits", e.getMessage());
        }
    }

    public void forceArm() {
        try {
            CommandLong command = CommandLong.builder()
                    .command(MavCmd.MAV_CMD_COMPONENT_ARM_DISARM)
                    .param1(1) // param1 = 1 (to arm the vehicle),
                    .param2(21196) // param2 = 21196 (magic number to bypass pre-arm checks and force arm)
                    .build();

            mav_conn.send1(255, 0, command);
        } catch (IOException e) {
            if (MyAppConfig.DEBUG) Log.d("chobits", "Failed to send force arm command: " + e.getMessage());
        }
    }
    public void disarm(){
        try{
            CommandLong command = CommandLong.builder()
                    .command(MavCmd.MAV_CMD_COMPONENT_ARM_DISARM)
                    .param1(0) // param1 = 1 (to arm the vehicle),
                    .param2(0)
                    .build();

            mav_conn.send1(255, 0, command);
        } catch (IOException e) {
            if (MyAppConfig.DEBUG) Log.d("chobits", "Failed to disarm: " + e.getMessage());
        }
    }

    public void rebootFC() {
        try {
            mav_conn.send1(255, 0, CommandLong.builder().command(MavCmd.MAV_CMD_PREFLIGHT_REBOOT_SHUTDOWN).param1(1).build());
        } catch (IOException e) {
            if (MyAppConfig.DEBUG) Log.d("chobits", e.getMessage());
        }
    }

    @Override
    public void run() {
        MavlinkMessage msg;
        while (true) {
            try {
                msg = mav_conn.next();
            } catch (IOException e) {
                if (MyAppConfig.DEBUG) Log.d("chobits", e.getMessage());
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
                if (hb.autopilot().entry() == MavAutopilot.MAV_AUTOPILOT_INVALID) continue;
                //Log.d("chobits", "heartbeat " + msg.getOriginSystemId() + "," + hb.customMode() + "," + msg.getSequence());
                vehicle = Vehicle.getInstance(hb.autopilot().entry(), hb.type().entry());
                Message ui_msg = ui_handler.obtainMessage(UI_AP_NAME, vehicle.Name());
                ui_handler.sendMessage(ui_msg);
                int flight_mode = (int)hb.customMode();
                ui_msg = ui_handler.obtainMessage(UI_FLIGHT_MODE, vehicle.Mode(flight_mode));
                ui_handler.sendMessage(ui_msg);
                if (flight_mode != prv_flight_mode) {
                    ui_msg = ui_handler.obtainMessage(UI_STATUS_TXT, 1, 1,  "flight mode " + vehicle.Mode(flight_mode));
                    ui_handler.sendMessage(ui_msg);
                    prv_flight_mode = flight_mode;
                }

                if (last_hb_ts == 0) {
                    ui_msg = ui_handler.obtainMessage(2, "vehicle " + msg.getOriginSystemId() + " connected " + DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date()));
                    ui_handler.sendMessage(ui_msg);
                }
                last_hb_ts = SystemClock.elapsedRealtime();

                //send hb to fc, keeping link active, otherwise we will not rcv status_txt
                try {
                    mav_conn.send1(255,0,Heartbeat.builder().type(MavType.MAV_TYPE_GCS).autopilot(MavAutopilot.MAV_AUTOPILOT_INVALID).mavlinkVersion(3).build());
                } catch (IOException e) {
                    if (MyAppConfig.DEBUG) Log.d("chobits", e.getMessage());
                }

                long ts = SystemClock.elapsedRealtime();
                if (ts - last_sys_status_ts > 3000) {
                    try {
                        mav_conn.send1(255, 0, CommandLong.builder().command(MavCmd.MAV_CMD_SET_MESSAGE_INTERVAL).param1(1).param2(1000000).build());
                    } catch (IOException e) {
                        if (MyAppConfig.DEBUG) Log.d("chobits", e.getMessage());
                    }
                }
                if (ts - last_gps_raw_ts > 3000) {
                    try {
                        mav_conn.send1(255, 0, CommandLong.builder().command(MavCmd.MAV_CMD_SET_MESSAGE_INTERVAL).param1(24).param2(1000000).build());
                    } catch (IOException e) {
                        if (MyAppConfig.DEBUG) Log.d("chobits", e.getMessage());
                    }
                }
                if (ts - last_global_pos_ts > 3000) {
                    try {
                        mav_conn.send1(255, 0, CommandLong.builder().command(MavCmd.MAV_CMD_SET_MESSAGE_INTERVAL).param1(33).param2(1000000).build());
                    } catch (IOException e) {
                        if (MyAppConfig.DEBUG) Log.d("chobits", e.getMessage());
                    }
                }
            } else if (msg_payload instanceof Statustext) {
                Statustext txt = (Statustext)msg_payload;
                if (MyAppConfig.DEBUG) Log.d("chobits", msg.getOriginSystemId() + "," + txt.text());
                int severity = 0;
                String text = txt.text();
                if (txt.severity().entry().ordinal() < 5) severity = 1;
                else if (text.startsWith("PrecLand")) severity = 1;
                Message ui_msg = ui_handler.obtainMessage(UI_STATUS_TXT, severity, 0,  text);
                ui_handler.sendMessage(ui_msg);
            } else if (msg_payload instanceof SysStatus) {
                last_sys_status_ts = SystemClock.elapsedRealtime();
                SysStatus status = (SysStatus)msg_payload;
                Message ui_msg = ui_handler.obtainMessage(UI_BAT_STATUS, status.voltageBattery(), status.currentBattery());
                ui_handler.sendMessage(ui_msg);
            } else if (msg_payload instanceof GpsRawInt) {
                last_gps_raw_ts = SystemClock.elapsedRealtime();
                GpsRawInt gps_raw = (GpsRawInt)msg_payload;
                Bundle data = new Bundle();
                data.putString("fix", GPS_FIX_TYPE[gps_raw.fixType().value()]);
                data.putString("hdop", String.format("%.1f", gps_raw.eph() * 0.01));
                data.putInt("satellites", gps_raw.satellitesVisible());
                Message ui_msg = ui_handler.obtainMessage(UI_GPS_STATUS);
                ui_msg.setData(data);
                ui_handler.sendMessage(ui_msg);
            } else if (msg_payload instanceof GlobalPositionInt) {
                last_global_pos_ts = SystemClock.elapsedRealtime();
                GlobalPositionInt global_pos = (GlobalPositionInt)msg_payload;
                Message ui_msg = ui_handler.obtainMessage(UI_GLOBAL_POS, global_pos.alt(), global_pos.relativeAlt());
                ui_handler.sendMessage(ui_msg);
            } else if (msg_payload instanceof ParamValue) {
                ParamValue p_val = (ParamValue)msg_payload;
                if (MyAppConfig.DEBUG) Log.d("chobits", "param val " + p_val.paramId() + ":" + p_val.paramValue());
                Message ui_msg = ui_handler.obtainMessage(UI_PARAM_VAL);
                Bundle data = new Bundle();
                data.putString("name", p_val.paramId());
                if (p_val.paramType().entry() == MavParamType.MAV_PARAM_TYPE_REAL32) {
                    data.putBoolean("is_float", true);
                    data.putFloat("val", p_val.paramValue());
                } else {
                    data.putBoolean("is_float", false);
                    data.putInt("val", (int)p_val.paramValue());
                }
                ui_msg.setData(data);
                ui_handler.sendMessage(ui_msg);
            } else if (msg_payload instanceof MissionItemReached) {
                Message ui_msg = ui_handler.obtainMessage(UI_STATUS_TXT, 1, 0,  "Waypoint reached");
                ui_handler.sendMessage(ui_msg);
            } else {
                //Log.d("chobits", msg.getPayload().getClass().getSimpleName());
            }
        }
    }
}
