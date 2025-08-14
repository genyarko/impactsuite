# API Keys Setup Guide

This document explains how to configure API keys for MyGemma3N's online features.

## Overview

MyGemma3N supports both **offline** and **online** modes:
- **Offline Mode**: Works completely offline using on-device AI models
- **Online Mode**: Enhanced features using cloud APIs (Google Gemini, Google Places, etc.)

## Required API Keys

### 1. Google Gemini API Key (Primary)
**Used for**: Enhanced AI responses, online translations, hospital guidance
**Required for**: All online features across the app

**How to get**:
1. Go to [Google AI Studio](https://aistudio.google.com/)
2. Create a new project or select existing one
3. Generate an API key
4. **Configure in app**: Go to Settings → API Settings → Enter your Gemini API key

### 2. Google Maps API Key (Optional)
**Used for**: Visual maps in Crisis Handbook feature
**Required for**: Maps display only (hospital data still works without this)

**How to get**:
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create/select a project
3. Enable the Maps SDK for Android API
4. Create credentials → API Key
5. **Configure in project**: 

#### Option A: Via gradle.properties (Recommended)
```properties
# In gradle.properties file
MAPS_API_KEY=none
```

#### Option B: Via Environment Variable
```bash
export MAPS_API_KEY=none
```

#### Option C: Direct in AndroidManifest.xml
```xml
<!-- In app/src/main/AndroidManifest.xml -->
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="none" />
```

## What Works Without API Keys

### Offline Mode (No API Keys Needed)
- ✅ All AI features using on-device Gemma models
- ✅ Image classification and plant disease detection  
- ✅ Quiz generation and AI tutoring
- ✅ Document summarization
- ✅ Live caption and translation
- ✅ Voice CBT coaching
- ✅ Crisis Handbook with offline hospital database
- ✅ All core functionality

### Partial Online Mode (Gemini API Only)
- ✅ Enhanced AI responses with cloud intelligence
- ✅ Better translation quality
- ✅ Online hospital search with AI guidance
- ❌ Visual maps (shows "Maps Not Available" message)

### Full Online Mode (All APIs)
- ✅ Everything from offline mode
- ✅ Enhanced cloud AI features
- ✅ Real-time hospital data from Google Places
- ✅ Visual maps with hospital locations
- ✅ Interactive navigation features

## API Key Security

### ⚠️ Important Security Notes:
- **Never commit API keys to version control**
- **Use environment variables or local config files**
- **Restrict API keys by application/domain in cloud console**
- **Monitor API usage and set billing limits**

### Recommended Security Setup:
1. **For Gemini API**: Enable Application Restrictions in AI Studio
2. **For Maps API**: Restrict to your app's package name + SHA-1 fingerprint
3. **Set Usage Quotas**: Prevent unexpected charges
4. **Use separate keys**: Dev vs Production environments

## Troubleshooting

### "Google Maps API key not found" Error
- **Cause**: Maps API key not configured in AndroidManifest.xml
- **Fix**: Set `MAPS_API_KEY` in gradle.properties or environment
- **Workaround**: App will show "Maps Not Available" but other features work

### "Online features not working"
- **Check**: Settings → API Settings → Verify Gemini API key is entered
- **Check**: Network connectivity
- **Check**: API key has proper permissions in Google AI Studio

### "Places API REQUEST_DENIED"
- **Cause**: Using legacy Places API or wrong API key
- **Fix**: Ensure Maps API key is properly configured
- **Result**: Falls back to offline hospital database

## Feature Matrix

| Feature | Offline | Online (Gemini) | Online (Full) |
|---------|---------|-----------------|---------------|
| AI Chat | ✅ | ✅ Enhanced | ✅ Enhanced |
| Plant Scanner | ✅ | ✅ Enhanced | ✅ Enhanced |
| Quiz Generation | ✅ | ✅ Enhanced | ✅ Enhanced |
| Document Summary | ✅ | ✅ Enhanced | ✅ Enhanced |
| Live Caption | ✅ | ✅ Enhanced | ✅ Enhanced |
| CBT Coach | ✅ | ✅ Enhanced | ✅ Enhanced |
| Crisis Handbook | ✅ Local DB | ✅ AI Guidance | ✅ + Live Maps |

## Development vs Production

### Development
```properties
# gradle.properties
MAPS_API_KEY=your_dev_maps_key
```

### Production  
```bash
# Environment variable or CI/CD
export MAPS_API_KEY=none
```

## Support

For API key setup issues:
1. Check this guide first
2. Verify API key permissions in respective cloud consoles
3. Test with minimal examples
4. Check app logs for specific error messages

**Remember**: The app is designed to work excellently offline. Online features are enhancements, not requirements!