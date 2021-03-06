/*
 * Copyright 2018 Farbod Salamat-Zadeh
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

package com.farbodsz.familytree.ui

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.MenuItem
import com.farbodsz.familytree.R
import com.farbodsz.familytree.ui.event.EventsActivity
import com.farbodsz.familytree.ui.home.MainActivity
import com.farbodsz.familytree.ui.person.PersonListActivity
import com.farbodsz.familytree.ui.tree.TreeActivity
import com.farbodsz.familytree.ui.tree.TreeListActivity

/**
 * Data class to contain details about navigation in a subclass of [NavigationDrawerActivity].
 *
 * @property navigationItem an integer indicating which navigation item the activity is used for
 * @property drawerLayout   the [DrawerLayout] used for navigation in the activity's layout
 * @property navigationView the [NavigationView] used for navigation in the activity's layout
 * @property toolbar        the [Toolbar] used as part of navigation in the activity's layout
 *
 * @see NavigationDrawerActivity.getSelfNavigationParams
 */
data class NavigationParameters(
        val navigationItem: Int,
        val drawerLayout: DrawerLayout,
        val navigationView: NavigationView,
        val toolbar: Toolbar
)

/**
 * An abstract activity used for implementing navigation drawer behaviour.
 * This should be implemented by activities which contain a navigation drawer.
 */
abstract class NavigationDrawerActivity : AppCompatActivity() {

    companion object {

        private const val LOG_TAG = "Nav...DrawerActivity"

        const val NAVDRAWER_ITEM_MAIN = R.id.nav_item_home
        const val NAVDRAWER_ITEM_TREE = R.id.nav_item_tree
        const val NAVDRAWER_ITEM_TREE_LIST = R.id.nav_item_tree_list
        const val NAVDRAWER_ITEM_PERSON_LIST = R.id.nav_item_person_list
        const val NAVDRAWER_ITEM_EVENTS = R.id.nav_item_events

        private const val NAVDRAWER_LAUNCH_DELAY = 250L
    }

    /**
     * Holds details about navigation behaviour in a subclass of this activity.
     */
    private lateinit var navParams: NavigationParameters

    private lateinit var drawerToggle: ActionBarDrawerToggle

    /**
     * Whether the navigation drawer is being used in the subclass of this activity.
     */
    private var usingNavDrawer: Boolean = false

    /**
     * This method should be overridden in subclasses of [NavigationDrawerActivity] to supply
     * details about the navigation behaviour.
     */
    abstract fun getSelfNavigationParams(): NavigationParameters?

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        val receivedNavParams = getSelfNavigationParams()
        navParams = if (receivedNavParams == null) {
            Log.d(LOG_TAG, "Navigation params null, not setting up navigation drawer")
            usingNavDrawer = false
            return
        } else {
            usingNavDrawer = true
            receivedNavParams
        }

        setupLayout()
    }

    private fun setupLayout() {
        setSupportActionBar(navParams.toolbar)

        with(navParams.navigationView) {
            menu.findItem(navParams.navigationItem).isChecked = true

            setNavigationItemSelectedListener({ menuItem ->
                handleNavigationSelection(menuItem)
                true
            })
        }

        drawerToggle = ActionBarDrawerToggle(
                this,
                navParams.drawerLayout,
                navParams.toolbar,
                R.string.drawer_open,
                R.string.drawer_close
        )

        navParams.drawerLayout.addDrawerListener(drawerToggle)

        drawerToggle.syncState()
    }

    private fun handleNavigationSelection(menuItem: MenuItem) {
        if (menuItem.itemId == navParams.navigationItem) {
            navParams.drawerLayout.closeDrawers()
            return
        }

        // Launch the target Activity after a short delay, to allow the close animation to play
        val handler = Handler()
        handler.postDelayed({ goToNavDrawerItem(menuItem.itemId) }, NAVDRAWER_LAUNCH_DELAY)

        if (menuItem.isCheckable) {
            navParams.navigationView.menu.findItem(navParams.navigationItem).isChecked = false
            menuItem.isChecked = true
        }

        navParams.drawerLayout.closeDrawers()
    }

    private fun goToNavDrawerItem(menuItem: Int) {
        val intent = when (menuItem) {
            NAVDRAWER_ITEM_MAIN -> Intent(this, MainActivity::class.java)
            NAVDRAWER_ITEM_TREE -> Intent(this, TreeActivity::class.java)
            NAVDRAWER_ITEM_TREE_LIST -> Intent(this, TreeListActivity::class.java)
            NAVDRAWER_ITEM_PERSON_LIST -> Intent(this, PersonListActivity::class.java)
            NAVDRAWER_ITEM_EVENTS -> Intent(this, EventsActivity::class.java)
            else -> throw IllegalArgumentException("unrecognised menu item: $menuItem")
        }

        startActivity(intent)
        finish()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (usingNavDrawer) {
            // Pass any configuration change to the drawer toggle
            drawerToggle.onConfigurationChanged(newConfig)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem) =
            (if (usingNavDrawer) drawerToggle.onOptionsItemSelected(item) else false)
                    || super.onOptionsItemSelected(item)

    override fun onBackPressed() =
            if (usingNavDrawer && navParams.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                navParams.drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                super.onBackPressed()
            }

}
