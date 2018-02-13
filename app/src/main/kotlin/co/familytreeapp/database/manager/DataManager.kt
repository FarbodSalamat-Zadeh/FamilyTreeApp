package co.familytreeapp.database.manager

import android.content.ContentValues
import android.content.Context
import android.util.Log
import co.familytreeapp.database.DatabaseHelper
import co.familytreeapp.database.query.Query
import co.familytreeapp.model.BaseItem

/**
 * A base data manager class, defining/implementing common methods.
 */
abstract class DataManager<T : BaseItem>(private val context: Context) {

    companion object {
        private const val LOG_TAG = "DataManager"
    }

    /**
     * The name of the table the subclass is managing.
     */
    abstract val tableName: String

    /**
     * The name of the ID column of the table the subclass is managing.
     */
    abstract val idColumn: String

    /**
     * Query the table corresponding to the data manager subclass.
     *
     * @return a list of items that satisfy the table [query]
     */
    abstract fun query(query: Query?): List<T>

    fun getAll() = query(null)

    abstract fun propertiesAsContentValues(item: T): ContentValues

    /**
     * Adds an item of type [T] to the table named [tableName].
     */
    fun add(item: T) {
        val db = DatabaseHelper.getInstance(context).writableDatabase
        db.insert(tableName, null, propertiesAsContentValues(item))
        Log.d(LOG_TAG, "Added item $item")
    }

    /**
     * Updates an item of type [T] with [oldItemId], replacing it with the new [item].
     *
     * @see add
     * @see delete
     */
    fun update(oldItemId: Int, item: T) {
        delete(oldItemId)
        add(item)
    }

    /**
     * Deletes an item of type [T] with specified [id] from the table named [tableName].
     *
     * @see deleteWithReferences
     */
    fun delete(id: Int) {
        val db = DatabaseHelper.getInstance(context).writableDatabase
        db.delete(tableName, idColumn, arrayOf(id.toString()))
        Log.d(LOG_TAG, "Deleted item (id: $id)")
    }

    /**
     * Deletes an item of type [T] with specified [id] and any other references to it.
     * This function should be overridden by subclasses of [DataManager] to specify which references
     * should be deleted.
     *
     * @see delete
     */
    open fun deleteWithReferences(id: Int) {
        delete(id)
    }

}
