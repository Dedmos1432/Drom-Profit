package ru.dedmos.dromprofit.domain

fun Car.toListingData() = ListingData(
    source = source,
    sourceUrl = sourceUrl,
    listingId = listingId,
    fetchedAt = fetchedAt,
    brand = brand,
    model = model,
    price = price,
    year = year,
    mileage = mileage,
    engineVolume = engineVolume,
    transmission = transmission,
    imageUrl = imageUrl,
    imageUrls = imageUrls,
    trim = trim,
    engineType = engineType,
    enginePower = enginePower,
    driveType = driveType,
    city = city,
    marketPrice = marketPrice
)
