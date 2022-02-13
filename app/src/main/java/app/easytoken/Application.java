/*
 * Application: one-time initialization at app startup
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

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.camera2.Camera2Config;
import androidx.camera.core.CameraXConfig;

public class Application extends android.app.Application implements CameraXConfig.Provider {

	@Override
	public void onCreate() {
		super.onCreate();
		TokenInfo.init(getApplicationContext());
	}

	@NonNull
	@Override
	public CameraXConfig getCameraXConfig() {
		return CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
				.setMinimumLoggingLevel(Log.ERROR).build();

	}
}
