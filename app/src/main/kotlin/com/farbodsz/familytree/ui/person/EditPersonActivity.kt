package com.farbodsz.familytree.ui.person

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.Snackbar
import android.support.design.widget.TextInputLayout
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.CardView
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import com.farbodsz.familytree.R
import com.farbodsz.familytree.database.manager.ChildrenManager
import com.farbodsz.familytree.database.manager.MarriagesManager
import com.farbodsz.familytree.database.manager.PersonManager
import com.farbodsz.familytree.model.Gender
import com.farbodsz.familytree.model.Marriage
import com.farbodsz.familytree.model.Person
import com.farbodsz.familytree.ui.DateRangeSelectorHelper
import com.farbodsz.familytree.ui.DateSelectorHelper
import com.farbodsz.familytree.ui.marriage.EditMarriageActivity
import com.farbodsz.familytree.ui.marriage.MarriageAdapter
import com.farbodsz.familytree.ui.validator.PersonValidator
import com.farbodsz.familytree.ui.widget.PersonCircleImageView
import com.farbodsz.familytree.util.IOUtils

/**
 * This activity provides the UI for adding or editing a new person from the database.
 *
 * When the user adds all the information and confirms, the data for the new person will be written
 * to the database, and the newly created [Person] will be sent back to the activity from which this
 * was started as a result.
 *
 * @see CreatePersonActivity
 */
class EditPersonActivity : AppCompatActivity() {

    companion object {

        private const val LOG_TAG = "EditPersonActivity"

        /**
         * Intent extra key for supplying a [Person] to this activity.
         */
        const val EXTRA_PERSON = "extra_person"

        /**
         * Request code for starting [EditPersonActivity] for result, to create a new [Person] which
         * would be the child of this [person].
         */
        private const val REQUEST_CREATE_CHILD = 4

        /**
         * Request code for starting [EditMarriageActivity] for result, to create a new [Marriage]
         */
        private const val REQUEST_CREATE_MARRIAGE = 5

        /**
         * Request code for selecting a person image from a "gallery" app on the device.
         */
        private const val REQUEST_PICK_IMAGE = 6

        /**
         * Represents an explicit MIME image type for use with [Intent.setType].
         */
        private const val MIME_IMAGE_TYPE = "image/*"
    }

    private val personManager = PersonManager(this)

    private lateinit var coordinatorLayout: CoordinatorLayout

    private lateinit var circleImageView: PersonCircleImageView
    private var bitmap: Bitmap? = null

    private lateinit var forenameInput: EditText
    private lateinit var surnameInput: EditText
    private lateinit var maleRadioBtn: RadioButton

    private lateinit var datesSelectorHelper: DateRangeSelectorHelper
    private lateinit var placeOfBirthInput: EditText
    private lateinit var isAliveCheckBox: CheckBox
    private lateinit var placeOfDeathInput: EditText

    private lateinit var marriageRecyclerView: RecyclerView

    private lateinit var childrenText: TextView
    private lateinit var childrenRecyclerView: RecyclerView

    /**
     * The [Person] received via intent extra from the previous activity. If a new person is being
     * created (hence no intent extra), then this will be null.
     *
     * This [Person] will not be affected by changes made in this activity.
     */
    private var person: Person? = null

    /**
     * The list of this [person]'s marriages that are displayed in the UI.
     * When the "Done" action is selected, these will be added to the database.
     */
    private lateinit var marriages: ArrayList<Marriage>

    /**
     * The list of this [person]'s children that are displayed in the UI.
     * When the "Done" action is selected, these will be added to the database.
     */
    private lateinit var children: ArrayList<Person>

    /**
     * True if the user has added or deleted any of the [person]'s [marriages].
     */
    private var hasModifiedMarriages = false

    /**
     * True if the user has added or deleted any of the [person]'s [children].
     */
    private var hasModifiedChildren = false

    private var hasModifiedBitmap = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_person)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        toolbar.setNavigationIcon(R.drawable.ic_close_white_24dp)
        toolbar.setNavigationOnClickListener { sendCancelledResult() }

        person = intent.extras?.getParcelable(EXTRA_PERSON)

        if (person == null) {
            Log.v(LOG_TAG, "Editing a new person")
        } else {
            Log.v(LOG_TAG, "Editing an existing person: $person")
        }

        assignUiComponents()

        setupLayout()
    }

    /**
     * Assigns the variables for our UI components in the layout.
     */
    private fun assignUiComponents() {
        coordinatorLayout = findViewById(R.id.coordinatorLayout)

        circleImageView = findViewById(R.id.circleImageView)
        circleImageView.setOnClickListener { selectPersonImage() }

        forenameInput = findViewById(R.id.editText_forename)
        surnameInput = findViewById(R.id.editText_surname)

        maleRadioBtn = findViewById(R.id.rBtn_male)
        maleRadioBtn.setOnCheckedChangeListener { _, isChecked ->
            val newGender = if (isChecked) Gender.MALE else Gender.FEMALE
            circleImageView.borderColor = ContextCompat.getColor(this, newGender.getColorRes())
        }
        // No need to use the female radio button - it will respond accordingly

        setupDatePickers()

        placeOfDeathInput = findViewById(R.id.editText_placeOfDeath)
        placeOfBirthInput = findViewById(R.id.editText_placeOfBirth)

        isAliveCheckBox = findViewById(R.id.checkbox_alive)
        isAliveCheckBox.setOnCheckedChangeListener { _, isChecked -> setPersonAlive(isChecked) }

        marriageRecyclerView = findViewById<RecyclerView>(R.id.recyclerView_marriages).apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@EditPersonActivity)
        }

        val addMarriageButton = findViewById<Button>(R.id.button_addMarriage)
        addMarriageButton.setOnClickListener {
            chooseMarriageDialog()
        }

        findViewById<CardView>(R.id.card_marriages).visibility =
                if (person == null) View.GONE else View.VISIBLE

        childrenText = findViewById(R.id.text_childrenNum)
        childrenText.text = resources.getQuantityText(R.plurals.children_count_subtitle, 0)

        childrenRecyclerView = findViewById<RecyclerView>(R.id.recyclerView_children).apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@EditPersonActivity)
        }

        val addChildButton = findViewById<Button>(R.id.button_addChild)
        addChildButton.setOnClickListener {
            chooseChildDialog()
        }
    }

    private fun setupDatePickers() {
        val dateOfBirthHelper = DateSelectorHelper(this, findViewById(R.id.editText_dateOfBirth))
        val dateOfDeathHelper = DateSelectorHelper(this, findViewById(R.id.editText_dateOfDeath))
        datesSelectorHelper = DateRangeSelectorHelper(dateOfBirthHelper, dateOfDeathHelper)
    }

    /**
     * Starts an [Intent] for result to pick an image from the gallery app.
     * The result will be sent to [onActivityResult].
     */
    private fun selectPersonImage() { // TODO too many similarities between this and CreatePersonActivity
        val getContentIntent = Intent(Intent.ACTION_GET_CONTENT).setType(MIME_IMAGE_TYPE)
        val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).setType(MIME_IMAGE_TYPE)

        val chooserIntent = Intent.createChooser(
                getContentIntent,
                getString(R.string.dialog_pickImage_title)
        ).putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(pickIntent))

        startActivityForResult(chooserIntent, REQUEST_PICK_IMAGE)
    }

    private fun setupLayout() {
        setupNameInputError(findViewById(R.id.textInputLayout_forename), forenameInput)
        setupNameInputError(findViewById(R.id.textInputLayout_surname), surnameInput)

        setupMarriageList()
        setupChildrenList()

        if (person == null) {
            Log.i(LOG_TAG, "Person is null - setting up the default layout")
            setupDefaultLayout()
            return
        }

        person?.let {
            circleImageView.person = it

            forenameInput.setText(it.forename)
            surnameInput.setText(it.surname)

            maleRadioBtn.isChecked = it.gender.isMale()

            setPersonAlive(it.isAlive())

            datesSelectorHelper.setDates(it.dateOfBirth, it.dateOfDeath)
        }
    }

    private fun setupNameInputError(textInputLayout: TextInputLayout, editText: EditText) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s!!.isBlank()) {
                    textInputLayout.error = getString(R.string.error_name_empty)
                } else {
                    textInputLayout.isErrorEnabled = false
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }
        })
        editText.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                if (editText.text.isBlank()) {
                    textInputLayout.error = getString(R.string.error_name_empty)
                } else {
                    textInputLayout.isErrorEnabled = false
                }
            }
        }
    }

    private fun setupDefaultLayout() {
        maleRadioBtn.isChecked = true
        setPersonAlive(true)
    }

    /**
     * Toggles the checkbox and other UI components for whether or not a person [isAlive].
     * This should be used instead of toggling the checkbox manually.
     */
    private fun setPersonAlive(isAlive: Boolean) {
        isAliveCheckBox.isChecked = isAlive
        findViewById<LinearLayout>(R.id.group_deathInfo).visibility =
                if (isAlive) View.GONE else View.VISIBLE
    }

    /**
     * Sets up [marriageRecyclerView] to display the marriages of the [Person] being edited.
     *
     * This should be invoked regardless of whether a new person is being added or an existing
     * person is being edited.
     */
    private fun setupMarriageList() {
        marriages = MarriagesManager(this).getMarriages(editedPersonId()) as ArrayList<Marriage>

        val marriageAdapter = MarriageAdapter(this, editedPersonId(), marriages)
        marriageAdapter.onItemClick { _, marriage ->
            // Show dialog with option to delete
            val options = arrayOf(getString(R.string.action_delete))

            AlertDialog.Builder(this).setTitle(getString(R.string.marriage_with, person?.forename ?: "null"))
                    .setItems(options) { _, which -> deleteMarriageFromUi(marriage) }
                    .setNegativeButton(android.R.string.cancel) { _, _ ->  }
                    .show()
        }

        marriageRecyclerView.adapter = marriageAdapter
    }

    /**
     * Updates the UI to add a [marriage] to the [Person] being edited.
     * Nothing is written to the database at this stage.
     *
     * @see deleteMarriageFromUi
     */
    private fun addMarriageToUi(marriage: Marriage) {
        hasModifiedMarriages = true
        marriages.add(marriage)
        marriageRecyclerView.adapter.notifyDataSetChanged()
    }

    /**
     * Updates the UI to delete a [marriage] from the [Person] being edited.
     * Nothing is deleted from the database at this stage.
     *
     * @see addMarriageToUi
     */
    private fun deleteMarriageFromUi(marriage: Marriage) {
        hasModifiedMarriages = true
        marriages.remove(marriage)
        marriageRecyclerView.adapter.notifyDataSetChanged()
    }

    /**
     * Sets up [childrenRecyclerView] to display the children of the [Person] being edited.
     *
     * This should be invoked regardless of whether a new person is being added or an existing
     * person is being edited.
     */
    private fun setupChildrenList() {
        val childrenManager = ChildrenManager(this)
        children = childrenManager.getChildren(editedPersonId()) as ArrayList<Person>

        childrenText.text = resources.getQuantityString(
                R.plurals.children_count_subtitle,
                children.count(),
                children.count()
        )

        val personAdapter = PersonAdapter(children)
        personAdapter.onItemClick { _, person ->
            // Show dialog with option to delete
            val options = arrayOf(getString(R.string.action_delete))

            AlertDialog.Builder(this).setTitle(person.fullName)
                    .setItems(options) { _, which -> deleteChildFromUi(person) }
                    .setNegativeButton(android.R.string.cancel) { _, _ ->  }
                    .show()
        }

        childrenRecyclerView.adapter = personAdapter
    }

    /**
     * Updates the UI to add a [child] to the [Person] being edited.
     * Nothing is written to the database at this stage.
     *
     * @see deleteChildFromUi
     */
    private fun addChildToUi(child: Person) {
        hasModifiedChildren = true
        children.add(child)
        childrenRecyclerView.adapter.notifyDataSetChanged()
        childrenText.text = resources.getQuantityString(
                R.plurals.children_count_subtitle,
                children.count(),
                children.count()
        )
    }

    /**
     * Updates the UI to delete a [child] from the [Person] being edited.
     * Nothing is deleted from the database at this stage.
     *
     * @see addChildToUi
     */
    private fun deleteChildFromUi(child: Person) {
        hasModifiedChildren = true
        children.remove(child)
        childrenRecyclerView.adapter.notifyDataSetChanged()
        childrenText.text = resources.getQuantityString(
                R.plurals.children_count_subtitle,
                children.count(),
                children.count()
        )
    }

    private fun chooseChildDialog() {
        lateinit var dialog: AlertDialog
        val builder = AlertDialog.Builder(this)

        val personAdapter = PersonAdapter(getPotentialChildren())
        personAdapter.onItemClick { _, person ->
            addChildToUi(person)
            dialog.dismiss()
        }

        val recyclerView = RecyclerView(this)
        with(recyclerView) {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@EditPersonActivity)
            adapter = personAdapter
        }

        val titleView = layoutInflater.inflate(R.layout.dialog_title_subtitle, null).apply {
            findViewById<TextView>(R.id.title).setText(R.string.dialog_add_child_title)
            findViewById<TextView>(R.id.subtitle).setText(R.string.dialog_add_child_subtitle)
        }

        builder.setView(recyclerView)
                .setCustomTitle(titleView)
                .setPositiveButton(R.string.action_create_new) { _, _ ->
                    val intent = Intent(this, EditPersonActivity::class.java)
                    startActivityForResult(intent, REQUEST_CREATE_CHILD)
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->  }

        dialog = builder.show()
    }

    /**
     * Returns a list of people that could be the child of this [person].
     *
     * This is people younger than this [person], not already considered a child of him/her, and not
     * the [parent][person] itself.
     */
    private fun getPotentialChildren(): List<Person> {
        val potentialChildren = ArrayList<Person>()

        // Ok to use this DOB as there were constraints/validation on the dialog picker
        val parentDob = datesSelectorHelper.getStartDate()

        for (child in personManager.getAll()) {
            if (child.id != editedPersonId()
                    && child !in children
                    && child.dateOfBirth.isAfter(parentDob)) {
                potentialChildren.add(child)
            }
        }
        return potentialChildren
    }

    private fun chooseMarriageDialog() {
        lateinit var dialog: AlertDialog
        val builder = AlertDialog.Builder(this)

        val dialogView = if (person == null) {
            TextView(this).apply {
                setText(R.string.db_marriages_empty)
            }
        } else {
            person?.let { // Use let for null safety on var
                val potentialMarriages = MarriagesManager(this).getMarriages(it.id)
                val marriageAdapter = MarriageAdapter(this, it.id, potentialMarriages)
                marriageAdapter.onItemClick { _, marriage ->
                    addMarriageToUi(marriage)
                    dialog.dismiss()
                }

                RecyclerView(this).apply {
                    setHasFixedSize(true)
                    layoutManager = LinearLayoutManager(this@EditPersonActivity)
                    adapter = marriageAdapter
                }
            }
        }

        builder.setView(dialogView)
                .setTitle(R.string.dialog_add_marriage_title)
                .setPositiveButton(R.string.action_create_new) { _, _ ->
                    val intent = Intent(this@EditPersonActivity, EditMarriageActivity::class.java)
                            .putExtra(EditMarriageActivity.EXTRA_WRITE_DATA, false)
                            .putExtra(EditMarriageActivity.EXTRA_EXISTING_PERSON, person)
                    startActivityForResult(intent, REQUEST_CREATE_MARRIAGE)
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->  }

        dialog = builder.show()
    }

    /**
     * Validates the user input and writes it to the database.
     */
    private fun saveData() {
        // Don't continue with db write if inputs invalid
        val newPerson = validatePerson() ?: return

        ChildrenManager(this).updateChildren(editedPersonId(), children)
        MarriagesManager(this).updateMarriages(editedPersonId(), marriages)

        var successful = false

        if (hasModifiedBitmap) {
            successful = true
            bitmap?.let { IOUtils.writePersonImage(it, newPerson.id, applicationContext) }
        }

        successful = when {
            person == null -> {
                personManager.add(newPerson)
                true
            }
            person!! == newPerson -> {
                // Person itself hasn't changed - only update if bitmap/children/marriages modified
                successful || hasModifiedChildren || hasModifiedMarriages
            }
            else -> {
                personManager.update(person!!.id, newPerson)
                true
            }
        }

        if (successful) {
            sendSuccessfulResult(newPerson)
        } else {
            sendCancelledResult()
        }
    }

    private fun validatePerson(): Person? {
        val validator = PersonValidator(
                coordinatorLayout,
                editedPersonId(),
                forenameInput.text.toString(),
                surnameInput.text.toString(),
                if (maleRadioBtn.isChecked) Gender.MALE else Gender.FEMALE,
                datesSelectorHelper.getStartDate(),
                placeOfBirthInput.text.toString(),
                if (isAliveCheckBox.isChecked) null else datesSelectorHelper.getEndDate(),
                placeOfDeathInput.text.toString()
        )
        return validator.validate()
    }

    /**
     * Returns the ID to be used for the [Person] being edited in this activity.
     * If the [Person] is being updated, this ID will remain the same as the original [Person]; if
     * it's a new [Person], it will be assigned a new ID.
     */
    private fun editedPersonId() = person?.id ?: personManager.nextAvailableId()

    /**
     * Sends an "ok" result back to where this activity was invoked from.
     *
     * @param result    the new/updated/deleted [Person]. If deleted this must be null.
     * @see android.app.Activity.RESULT_OK
     */
    private fun sendSuccessfulResult(result: Person?) {
        Log.d(LOG_TAG, "Sending successful result: $result")
        val returnIntent = Intent().putExtra(EXTRA_PERSON, result)
        setResult(Activity.RESULT_OK, returnIntent)
        finish()
    }

    /**
     * Sends a "cancelled" result back to where this activity was invoked from.
     *
     * @see android.app.Activity.RESULT_CANCELED
     */
    private fun sendCancelledResult() {
        Log.d(LOG_TAG, "Sending cancelled result")
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_CREATE_CHILD -> if (resultCode == Activity.RESULT_OK) {
                // User has successfully created a new child from the dialog
                val child = data!!.getParcelableExtra<Person>(CreatePersonActivity.EXTRA_PERSON)
                addChildToUi(child)
            }

            REQUEST_CREATE_MARRIAGE -> if (resultCode == Activity.RESULT_OK) {
                // User has successfully created a new marriage from the dialog
                val marriage = data!!.getParcelableExtra<Marriage>(EditMarriageActivity.EXTRA_MARRIAGE)
                addMarriageToUi(marriage)
            }

            REQUEST_PICK_IMAGE -> {
                if (data == null) {
                    Snackbar.make(
                            coordinatorLayout,
                            R.string.error_couldntChangeImage,
                            Snackbar.LENGTH_SHORT
                    ).show()
                } else {
                    val imageUri = data.data
                    bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                    hasModifiedBitmap = true
                    circleImageView.setImageBitmap(bitmap)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.menu_edit, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item!!.itemId) {
            R.id.action_done -> saveData()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() = sendCancelledResult()

}
