/*
 * MainActivity: displays GettingStartedFragment and TokencodeFragment
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

import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity
		implements GettingStartedFragment.OnImportButtonClickedListener,
					TokencodeFragment.OnTokenListChangeHandler {

	public static final String TAG = "EasyToken";

	private static final String EXTRA_LAST_MODIFIED = "app.easytoken.last_modified";
	private static final String EXTRA_TOKEN_PRESENT = "app.easytoken.token_present";

	private long mLastModified;
	private boolean mTokenPresent;
	ArrayAdapter<TokenInfo> listAdapter;

	private void updateVisibility() {
		int vis = mTokenPresent ? View.GONE : View.VISIBLE;
		int visList = mTokenPresent ? View.VISIBLE : View.GONE;

		findViewById(R.id.divider).setVisibility(vis);
		findViewById(R.id.frag_1).setVisibility(vis);
		findViewById(R.id.frag_0).setVisibility(vis);
		findViewById(R.id.token_list).setVisibility(visList);
	}

	private void setupFragment() {
		Fragment frag;

		mLastModified = TokenInfo.lastModified;
		ArrayList<TokenInfo> infoItems = TokenInfo.getTokens();
		if (infoItems != null) {
			listAdapter = new TokencodeFragment(this, R.layout.token_diag_info, infoItems);
			ListView lv = (ListView)findViewById(R.id.token_list);
			lv.setAdapter(listAdapter);
			listAdapter.notifyDataSetChanged();
			mTokenPresent = true;
			updateVisibility();
		} else {
			frag = new GettingStartedFragment();

			getFragmentManager().beginTransaction()
				.replace(R.id.frag_1, new DevidFragment())
				.commit();

			mTokenPresent = false;
			updateVisibility();
			getFragmentManager().beginTransaction().replace(R.id.frag_0, frag).commit();
		}
	}

	@Override
	public void onCreate(Bundle b) {
		super.onCreate(b);

		setContentView(R.layout.activity_main);
		if (b == null) {
			setupFragment();
		} else {
			mLastModified = b.getLong(EXTRA_LAST_MODIFIED);
			mTokenPresent = b.getBoolean(EXTRA_TOKEN_PRESENT);
			updateVisibility();
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle b) {
		super.onSaveInstanceState(b);
		b.putLong(EXTRA_LAST_MODIFIED, mLastModified);
		b.putBoolean(EXTRA_TOKEN_PRESENT, mTokenPresent);
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (TokenInfo.lastModified != mLastModified) {
			setupFragment();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_import:
			onImportButtonClicked();
			return true;
		case R.id.action_settings:
			BareActivity.startWithFrag(this, SettingsFragment.class);
			return true;
		case R.id.action_help:
			BareActivity.startWithLayout(this, R.layout.activity_help);
			return true;
		case R.id.action_about:
			BareActivity.startWithLayout(this, R.layout.activity_about);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onImportButtonClicked() {
		// clicking either "Import new token" from the menu, or "Import token" from
		// GettingStartedFragment, will end up here
		startActivity(new Intent(this, ImportActivity.class));
	}

	@Override
	public void onTokenListChanged() {
		if (TokenInfo.lastModified != mLastModified) {
			setupFragment();
		}
	}
}
