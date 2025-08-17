package com.mygemma3n.aiapp.feature.story

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class CharacterCreatorViewModel @Inject constructor(
    private val characterRepository: CharacterRepository
) : ViewModel() {

    data class CharacterCreatorState(
        val showCharacterList: Boolean = true,
        val existingCharacters: List<Character> = emptyList(),
        val currentCharacter: Character = getDefaultCharacter(),
        val isLoading: Boolean = false,
        val error: String? = null,
        val isEditing: Boolean = false
    )

    private val _state = MutableStateFlow(CharacterCreatorState())
    val state: StateFlow<CharacterCreatorState> = _state.asStateFlow()

    init {
        loadExistingCharacters()
        initializePresetCharacters()
    }

    private fun loadExistingCharacters() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true)
                
                characterRepository.getAllActiveCharacters().collect { characters ->
                    _state.value = _state.value.copy(
                        existingCharacters = characters,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load characters")
                _state.value = _state.value.copy(
                    error = "Failed to load characters: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    private fun initializePresetCharacters() {
        viewModelScope.launch {
            try {
                // Only create presets if no characters exist
                val existingCount = characterRepository.getAllActiveCharacters().first().size
                if (existingCount == 0) {
                    characterRepository.createPresetCharacters()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize preset characters")
            }
        }
    }

    fun startCreatingNewCharacter() {
        _state.value = _state.value.copy(
            showCharacterList = false,
            currentCharacter = getDefaultCharacter(),
            isEditing = false
        )
    }

    fun editCharacter(character: Character) {
        _state.value = _state.value.copy(
            showCharacterList = false,
            currentCharacter = character,
            isEditing = true
        )
    }

    fun updateCurrentCharacter(character: Character) {
        _state.value = _state.value.copy(currentCharacter = character)
    }

    fun showCharacterList() {
        _state.value = _state.value.copy(
            showCharacterList = true,
            currentCharacter = getDefaultCharacter(),
            isEditing = false
        )
    }

    fun saveCharacter(onSaved: (Character) -> Unit) {
        viewModelScope.launch {
            try {
                val character = _state.value.currentCharacter
                
                if (character.name.isBlank()) {
                    _state.value = _state.value.copy(error = "Character name is required")
                    return@launch
                }

                if (_state.value.isEditing) {
                    characterRepository.updateCharacter(character)
                } else {
                    characterRepository.saveCharacter(character)
                }

                onSaved(character)
                showCharacterList()
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to save character")
                _state.value = _state.value.copy(
                    error = "Failed to save character: ${e.message}"
                )
            }
        }
    }

    fun deleteCharacter(character: Character) {
        viewModelScope.launch {
            try {
                characterRepository.deleteCharacter(character.id)
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete character")
                _state.value = _state.value.copy(
                    error = "Failed to delete character: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    companion object {
        private fun getDefaultCharacter() = Character(
            name = "",
            gender = Gender.UNSPECIFIED,
            ageGroup = AgeGroup.CHILD,
            hairColor = HairColor.BROWN,
            eyeColor = EyeColor.BROWN,
            skinTone = SkinTone.MEDIUM,
            bodyType = BodyType.AVERAGE,
            personalityTraits = emptyList(),
            specialAbilities = listOf(SpecialAbility.NONE),
            characterRole = CharacterRole.PROTAGONIST,
            backstory = "",
            favoriteThings = emptyList(),
            fears = emptyList(),
            goals = "",
            catchphrase = "",
            occupation = "",
            homeland = "",
            relationships = ""
        )
    }
}