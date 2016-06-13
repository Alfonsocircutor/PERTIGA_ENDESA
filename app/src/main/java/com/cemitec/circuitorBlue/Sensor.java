package com.cemitec.circuitorBlue;

import android.bluetooth.BluetoothGattCharacteristic;

import static java.lang.Math.pow;


/**
 * This enum encapsulates the differences amongst the sensors. The differences include UUID values and how to interpret the
 * characteristic-containing-measurement.
 */
public enum Sensor {
    IR_TEMPERATURE() {
        @Override
        public Point3D convert(final byte [] value) {

			/*
			 * The IR Temperature sensor produces two measurements; Object ( AKA target or IR) Temperature, and Ambient ( AKA die ) temperature.
			 * Both need some conversion, and Object temperature is dependent on Ambient temperature.
			 * They are stored as [ObjLSB, ObjMSB, AmbLSB, AmbMSB] (4 bytes) Which means we need to shift the bytes around to get the correct values.
			 */

            double ambient = extractAmbientTemperature(value);
            double target = extractTargetTemperature(value, ambient);
            double targetNewSensor = extractTargetTemperatureTMP007(value);
            return new Point3D(ambient, target, targetNewSensor);
        }
        //Extrae la tensión de la muestra numMuestra de las 6 que van en cada notificación. byte[] c es de 18 bytes


        private double extractAmbientTemperature(byte [] v) {
            int offset = 2;
            return shortUnsignedAtOffset(v, offset) / 128.0;
        }

        private double extractTargetTemperature(byte [] v, double ambient) {
            Integer twoByteValue = shortSignedAtOffset(v, 0);

            double Vobj2 = twoByteValue.doubleValue();
            Vobj2 *= 0.00000015625;

            double Tdie = ambient + 273.15;

            double S0 = 5.593E-14; // Calibration factor
            double a1 = 1.75E-3;
            double a2 = -1.678E-5;
            double b0 = -2.94E-5;
            double b1 = -5.7E-7;
            double b2 = 4.63E-9;
            double c2 = 13.4;
            double Tref = 298.15;
            double S = S0 * (1 + a1 * (Tdie - Tref) + a2 * pow((Tdie - Tref), 2));
            double Vos = b0 + b1 * (Tdie - Tref) + b2 * pow((Tdie - Tref), 2);
            double fObj = (Vobj2 - Vos) + c2 * pow((Vobj2 - Vos), 2);
            double tObj = pow(pow(Tdie, 4) + (fObj / S), .25);

            return tObj - 273.15;
        }
        private double extractTargetTemperatureTMP007(byte [] v) {
            int offset = 0;
            return shortUnsignedAtOffset(v, offset) / 128.0;
        }
    };


    public static int extractTension(byte[] c, int numMuestra)
    {
        int byteIndex = 3*numMuestra;

        int lowerByte = (int) c[byteIndex] & 0xFF;
        int lowerNible = (int) c[byteIndex+1] & 0x0F;
        return lowerByte |(lowerNible << 8);
    }

    public static int extractIntensidad(byte[] c, int numMuestra)
    {
        int byteIndex = 3*numMuestra;
        int  byte1, byte2;

        byte1 = c[byteIndex+1];
        byte2 = c[byteIndex+2];
        Integer upperByte = (int) (((byte1 & 0xF0)>>4) | ((byte2 & 0xFF)<<4));
        return upperByte;
    }
    public static boolean detectaMarca(byte[] c){

        int numMuestra = 4; // 2 ultimas muestras
        int byteIndex = 3*numMuestra;

        byte temp;

        temp = (byte)(c[byteIndex] & c[byteIndex + 1] & c[byteIndex + 2] & c[byteIndex + 3] & c[byteIndex + 4] & c[byteIndex + 5]);

        if((temp & 0xFF)== 0xa5){

            return true;
        }
        else{
            return false;
        }
    }

    /**
     * Gyroscope, Magnetometer, Barometer, IR temperature all store 16 bit two's complement values as LSB MSB, which cannot be directly parsed
     * as getIntValue(FORMAT_SINT16, offset) because the bytes are stored as little-endian.
     *
     * This function extracts these 16 bit two's complement values.
     * */
    private static Integer shortSignedAtOffset(byte[] c, int offset) {
        Integer lowerByte = (int) c[offset] & 0xFF;
        Integer upperByte = (int) c[offset+1]; // // Interpret MSB as signed
        return (upperByte << 8) + lowerByte;
    }

    private static Integer shortUnsignedAtOffset(byte[] c, int offset) {
        Integer lowerByte = (int) c[offset] & 0xFF;
        Integer upperByte = (int) c[offset+1] & 0xFF;
        return (upperByte << 8) + lowerByte;
    }
    private static Integer twentyFourBitUnsignedAtOffset(byte[] c, int offset) {
        Integer lowerByte = (int) c[offset] & 0xFF;
        Integer mediumByte = (int) c[offset+1] & 0xFF;
        Integer upperByte = (int) c[offset + 2] & 0xFF;
        return (upperByte << 16) + (mediumByte << 8) + lowerByte;
    }

    public void onCharacteristicChanged(BluetoothGattCharacteristic c) {
        throw new UnsupportedOperationException("Error: the individual enum classes are supposed to override this method.");
    }


    public Point3D convert(byte[] value) {
        throw new UnsupportedOperationException("Error: the individual enum classes are supposed to override this method.");
    }



   // private final UUID service, data, config;
    private byte enableCode; // See getEnableSensorCode for explanation.
    public static final byte DISABLE_SENSOR_CODE = 0;
    public static final byte ENABLE_SENSOR_CODE = 1;
    public static final byte CALIBRATE_SENSOR_CODE = 2;

    /**
     * Constructor called by the Gyroscope and Accelerometer because it more than a boolean enable
     * code.
     */
  /*  private Sensor(UUID service, UUID data, UUID config, byte enableCode) {
        this.service = service;
        this.data = data;
        this.config = config;
        this.enableCode = enableCode;
    }*/

    /**
     * Constructor called by all the sensors except Gyroscope
     * */
  /*  private Sensor(UUID service, UUID data, UUID config) {
        this.service = service;
        this.data = data;
        this.config = config;
        this.enableCode = ENABLE_SENSOR_CODE; // This is the sensor enable code for all sensors except the gyroscope
    }*/

    /**
     * @return the code which, when written to the configuration characteristic, turns on the sensor.
     * */
    public byte getEnableSensorCode() {
        return enableCode;
    }

  /*  public UUID getService() {
        return service;
    }

    public UUID getData() {
        return data;
    }

    public UUID getConfig() {
        return config;
    }*/

   /* public static Sensor getFromDataUuid(UUID uuid) {
        for (Sensor s : Sensor.values()) {
            if (s.getData().equals(uuid)) {
                return s;
            }
        }
        throw new RuntimeException("unable to find UUID.");
    }*/

   public static final Sensor[] SENSOR_LIST = {IR_TEMPERATURE};
}
