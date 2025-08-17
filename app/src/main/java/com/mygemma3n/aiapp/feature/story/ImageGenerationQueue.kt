package com.mygemma3n.aiapp.feature.story

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages a queue of image generation tasks with retry logic.
 * This allows for robust background image generation without blocking story creation.
 */
@Singleton
class ImageGenerationQueue @Inject constructor(
    private val onlineStoryGenerator: OnlineStoryGenerator,
    private val storyRepository: StoryRepository
) {
    
    // Callback for when a story is updated with images
    private var onStoryUpdatedCallback: ((Story) -> Unit)? = null
    
    data class ImageGenerationTask(
        val storyId: String,
        val story: Story,
        val retryCount: Int = 0,
        val maxRetries: Int = 2
    )
    
    data class QueueStatus(
        val isProcessing: Boolean = false,
        val queueSize: Int = 0,
        val completedTasks: Int = 0,
        val failedTasks: Int = 0
    )
    
    private val taskQueue = ConcurrentLinkedQueue<ImageGenerationTask>()
    private val _queueStatus = MutableStateFlow(QueueStatus())
    val queueStatus: StateFlow<QueueStatus> = _queueStatus.asStateFlow()
    
    private var processingJob: Job? = null
    private var completedCount = 0
    private var failedCount = 0
    
    /**
     * Sets a callback that will be called when a story is updated with new images
     */
    fun setOnStoryUpdatedCallback(callback: (Story) -> Unit) {
        onStoryUpdatedCallback = callback
    }

    /**
     * Adds a story to the image generation queue
     */
    fun enqueueImageGeneration(story: Story) {
        if (!shouldGenerateImages(story.targetAudience)) {
            Timber.d("Story ${story.id} does not need images, skipping queue")
            return
        }
        
        // Check if task already exists for this story
        val existingTask = taskQueue.find { it.storyId == story.id }
        if (existingTask != null) {
            Timber.d("Image generation task already queued for story ${story.id}")
            return
        }
        
        val task = ImageGenerationTask(story.id, story)
        taskQueue.offer(task)
        
        updateQueueStatus()
        
        Timber.d("Added story ${story.id} to image generation queue. Queue size: ${taskQueue.size}")
        
        // Start processing if not already running
        if (processingJob == null || processingJob?.isActive != true) {
            startProcessing()
        }
    }
    
    /**
     * Starts processing the queue in the background
     */
    private fun startProcessing() {
        processingJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            _queueStatus.value = _queueStatus.value.copy(isProcessing = true)
            
            while (taskQueue.isNotEmpty()) {
                val task = taskQueue.poll()
                if (task != null) {
                    processTask(task)
                    updateQueueStatus()
                }
            }
            
            _queueStatus.value = _queueStatus.value.copy(isProcessing = false)
            Timber.d("Image generation queue processing completed")
        }
    }
    
    /**
     * Processes a single image generation task
     */
    private suspend fun processTask(task: ImageGenerationTask) {
        try {
            Timber.d("Processing image generation for story ${task.storyId} (attempt ${task.retryCount + 1})")
            
            val storyWithImages = onlineStoryGenerator.generateImagesForExistingStory(task.story)
            
            if (storyWithImages != null && storyWithImages.hasImages) {
                // Successfully generated images, update the database
                storyRepository.updateStory(storyWithImages)
                completedCount++
                Timber.d("Successfully generated images for story ${task.storyId}")
                
                // Notify callback that the story was updated
                onStoryUpdatedCallback?.invoke(storyWithImages)
            } else {
                // Failed to generate images, retry if possible
                if (task.retryCount < task.maxRetries) {
                    val retryTask = task.copy(retryCount = task.retryCount + 1)
                    taskQueue.offer(retryTask)
                    Timber.w("Image generation failed for story ${task.storyId}, retrying (${task.retryCount + 1}/${task.maxRetries})")
                } else {
                    failedCount++
                    Timber.e("Image generation failed for story ${task.storyId} after ${task.maxRetries} retries")
                }
            }
        } catch (e: Exception) {
            // Handle task failure
            if (task.retryCount < task.maxRetries) {
                val retryTask = task.copy(retryCount = task.retryCount + 1)
                taskQueue.offer(retryTask)
                Timber.w(e, "Image generation error for story ${task.storyId}, retrying (${task.retryCount + 1}/${task.maxRetries})")
            } else {
                failedCount++
                Timber.e(e, "Image generation failed for story ${task.storyId} after ${task.maxRetries} retries")
            }
        }
    }
    
    /**
     * Updates the queue status for monitoring
     */
    private fun updateQueueStatus() {
        _queueStatus.value = QueueStatus(
            isProcessing = processingJob?.isActive == true,
            queueSize = taskQueue.size,
            completedTasks = completedCount,
            failedTasks = failedCount
        )
    }
    
    /**
     * Clears the queue and stops processing
     */
    fun clearQueue() {
        taskQueue.clear()
        processingJob?.cancel()
        processingJob = null
        updateQueueStatus()
        Timber.d("Image generation queue cleared")
    }
    
    /**
     * Retries failed image generation for a specific story
     */
    fun retryImageGeneration(story: Story) {
        enqueueImageGeneration(story)
    }
    
    private fun shouldGenerateImages(targetAudience: StoryTarget): Boolean {
        return when (targetAudience) {
            StoryTarget.KINDERGARTEN, StoryTarget.ELEMENTARY, StoryTarget.MIDDLE_SCHOOL -> true
            StoryTarget.HIGH_SCHOOL, StoryTarget.ADULT -> false
        }
    }
}