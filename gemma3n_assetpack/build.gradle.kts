plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("gemma3n_assetpack")
    dynamicDelivery {
        deliveryType.set("install-time")
    }
}
