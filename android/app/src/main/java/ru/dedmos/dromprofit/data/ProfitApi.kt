package ru.dedmos.dromprofit.data

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

internal interface ProfitApi {
    @POST("api/session")
    suspend fun createSession(): ResponseBody

    @GET("api/cars")
    suspend fun getCars(@Header("Authorization") token: String): ResponseBody

    @GET("api/cars/{car_id}")
    suspend fun getCar(
        @Header("Authorization") token: String,
        @Path("car_id") carId: String
    ): ResponseBody

    @POST("api/cars/import")
    suspend fun importListing(
        @Header("Authorization") token: String,
        @Body body: RequestBody
    ): ResponseBody

    @POST("api/cars")
    suspend fun createCar(
        @Header("Authorization") token: String,
        @Body body: RequestBody
    ): ResponseBody

    @PATCH("api/cars/{car_id}")
    suspend fun updateCar(
        @Header("Authorization") token: String,
        @Path("car_id") carId: String,
        @Body body: RequestBody
    ): ResponseBody

    @DELETE("api/cars/{car_id}")
    suspend fun deleteCar(
        @Header("Authorization") token: String,
        @Path("car_id") carId: String
    ): Response<Unit>

    @POST("api/cars/{car_id}/expenses")
    suspend fun createExpense(
        @Header("Authorization") token: String,
        @Path("car_id") carId: String,
        @Body body: RequestBody
    ): ResponseBody

    @PATCH("api/cars/{car_id}/expenses/{item_id}")
    suspend fun updateExpense(
        @Header("Authorization") token: String,
        @Path("car_id") carId: String,
        @Path("item_id") expenseId: String,
        @Body body: RequestBody
    ): ResponseBody

    @DELETE("api/cars/{car_id}/expenses/{item_id}")
    suspend fun deleteExpense(
        @Header("Authorization") token: String,
        @Path("car_id") carId: String,
        @Path("item_id") expenseId: String
    ): Response<Unit>

    @POST("api/cars/{car_id}/benefits")
    suspend fun createBenefit(
        @Header("Authorization") token: String,
        @Path("car_id") carId: String,
        @Body body: RequestBody
    ): ResponseBody

    @PATCH("api/cars/{car_id}/benefits/{item_id}")
    suspend fun updateBenefit(
        @Header("Authorization") token: String,
        @Path("car_id") carId: String,
        @Path("item_id") benefitId: String,
        @Body body: RequestBody
    ): ResponseBody

    @DELETE("api/cars/{car_id}/benefits/{item_id}")
    suspend fun deleteBenefit(
        @Header("Authorization") token: String,
        @Path("car_id") carId: String,
        @Path("item_id") benefitId: String
    ): Response<Unit>

    @POST("api/cars/{car_id}/notes")
    suspend fun createNote(
        @Header("Authorization") token: String,
        @Path("car_id") carId: String,
        @Body body: RequestBody
    ): ResponseBody

    @PATCH("api/cars/{car_id}/notes/{item_id}")
    suspend fun updateNote(
        @Header("Authorization") token: String,
        @Path("car_id") carId: String,
        @Path("item_id") noteId: String,
        @Body body: RequestBody
    ): ResponseBody

    @DELETE("api/cars/{car_id}/notes/{item_id}")
    suspend fun deleteNote(
        @Header("Authorization") token: String,
        @Path("car_id") carId: String,
        @Path("item_id") noteId: String
    ): Response<Unit>

    @GET("api/cars/{car_id}/summary")
    suspend fun getSummary(
        @Header("Authorization") token: String,
        @Path("car_id") carId: String
    ): ResponseBody

    @POST("api/comparison")
    suspend fun compare(
        @Header("Authorization") token: String,
        @Body body: RequestBody
    ): ResponseBody
}
