/*
 * TokenInfo: container for token info; functions for loading/saving tokens
 *
 * This file is part of Easy Token
 * Copyright (c) 2014, Kevin Cernekee <cernekee@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package app.easytoken;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import android.util.Log;

import org.jetbrains.annotations.Nullable;

import us.berkovitz.stoken.SecurIdToken;


public class TokenInfo {

	public static final String TAG = "EasyToken";

	public SecurIdToken token;
	public String name;
	public String pin;
	public boolean pinRequired;

	public static long lastModified;

	private static SharedPreferences mPrefs;
	private static boolean mSavePin;
	private static Set<String> mTokens;
	private static String mDeviceId;

	public static void init(Context context) {
		mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
		mSavePin = mPrefs.getBoolean("save_pin", true);
		mTokens = mPrefs.getStringSet("token_ids", Collections.emptySet());

		/*
		 * ANDROID_ID is unique, but it is only 64 bits long.  So truncate its SHA1 hash to 12 bytes.
		 * Also, store it in mPrefs in case the ROM provides a random value on each access/boot/whatever.
		 * Because we don't want to get a bound token issued for a random device ID.
		 */
		mDeviceId = mPrefs.getString("device_id", null);
		if (mDeviceId == null) {
			String id = sha1(Secure.getString(context.getContentResolver(), Secure.ANDROID_ID));
			mDeviceId = id.substring(0, 24);
			mPrefs.edit().putString("device_id", mDeviceId).commit();
		}
	}

	private static String sha1(String input) {
		try {
		    MessageDigest digest = MessageDigest.getInstance("SHA1");
		    digest.reset();

		    byte[] byteData = digest.digest(input.getBytes("UTF-8"));
		    StringBuffer sb = new StringBuffer();

		    for (int i = 0; i < byteData.length; i++){
		      sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
		    }
		    return sb.toString().toUpperCase(Locale.getDefault());
		} catch (Exception e) {
			Log.e(TAG, "unable to compute SHA1 hash", e);
		}
		return "000000000000000000000000";
	}

	public static String getDeviceId() {
		return mDeviceId;
	}

	private static String getTokenString(String id) {
		return mPrefs.getString("token_str_" + id, null);
	}

	public static TokenInfo getToken(String serial) {
		String s = getTokenString(serial);

		if (s == null) {
			Log.e(TAG, "tried to access nonexistent token string #" + serial);
			return null;
		}

		SecurIdToken token;
		try {
			token = SecurIdToken.Companion.importString(s, false);
		} catch (Exception exc) {
			Log.e(TAG, "error importing token string #" + serial + ": error " + exc);
			return null;
		}

		try {
			token.decryptSeed("", "");
		} catch (Exception exc){
			Log.e(TAG, "error decrypting token string #" + serial + ": error " + exc);
			return null;
		}

		return new TokenInfo(token,
				mPrefs.getString("token_pin_" + serial, null),
				mPrefs.getString("token_name_" + serial, "UNKNOWN"));
	}

	public static ArrayList<TokenInfo> getTokens() {
		mTokens = mPrefs.getStringSet("token_ids", Collections.emptySet());
		if (mTokens.size() == 0) {
			return null;
		}
		ArrayList<TokenInfo> retList = new ArrayList<>();
		for (String tokenStr: mTokens) {
			retList.add(getToken(tokenStr));
		}

		return retList;
	}

	/*
	 * Notes on using the public constructors:
	 *
	 * LIB must already contain a successfully decrypted token.
	 *
	 * There is no way to save invalid or encrypted seeds in the preferences.  save() will
	 * re-encode the seed into a standard format.
	 *
	 * Do not call lib.destroy() if LIB is owned by a TokenInfo instance.
	 */
	public TokenInfo(SecurIdToken token, String pin, String name) {
		this.token = token;
		this.pin = (pin == null) ? "" : pin;
		this.name = name;

		// This changes to false if computeTokencode() is called with a PIN
		this.pinRequired = token.pinRequired();
	}

	public TokenInfo(SecurIdToken token, String pin) {
		this(token, pin, token.getSerial());
	}

	public void delete() {
		lastModified = System.currentTimeMillis();
		HashSet<String> newTokens = new HashSet<>();
		for (String serial: mTokens) {
			if(!serial.equals(token.getSerial()))
				newTokens.add(serial);
		}

		mPrefs.edit()
			.remove("token_str_" + token.getSerial())
			.remove("token_pin_" + token.getSerial())
			.remove("token_name_" + token.getSerial())
			.putStringSet("token_ids", newTokens)
			.commit();
	}

	/* returns true if changed, false otherwise */
	private boolean writePrefString(Editor ed, String key, @Nullable String value) {
		String old = mPrefs.getString(key, null);
		if ((value == null && old == null) || (value != null && value.equals(old))) {
			return false;
		}
		ed.putString(key, value);
		return true;
	}

	public String save() {
		lastModified = System.currentTimeMillis();
		HashSet<String> newTokens = new HashSet<>(mTokens);
		newTokens.add(token.getSerial());

		Editor ed = mPrefs.edit();

		writePrefString(ed, "token_str_" + token.getSerial(), token.encodeToken("", "", 2));
		writePrefString(ed, "token_name_" + token.getSerial(), name);
		ed.putStringSet("token_ids", newTokens);

		if (mSavePin) {
			writePrefString(ed, "token_pin_" + token.getSerial(), pin);
		} else {
			writePrefString(ed, "token_pin_" + token.getSerial(), null);
		}
		ed.commit();
		return token.getSerial();
	}

	public static void setSavePin(boolean val) {
		mSavePin = val;

		/* on deselection, clear all saved PINs */
		Editor ed = mPrefs.edit();
		for (String serial: mTokens) {
			ed.remove("token_pin_" + serial);
		}
		ed.commit();
	}

	public boolean isPinMissing() {
		return pinRequired && pin.equals("");
	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
	}

}
