package com.example.safepath_test1.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.safepath_test1.R
import com.example.safepath_test1.location.KakaoPlace
import com.example.safepath_test1.location.KakaoPlaceRepository
import com.example.safepath_test1.location.MultiRouteResult
import com.example.safepath_test1.location.NavigationRepository
import com.example.safepath_test1.model.GeoPoint
import com.example.safepath_test1.model.PlaceSelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val routes: MultiRouteResult? = null,
    val isRouteLoading: Boolean = false,
    val routeError: String? = null,
    val routeNotice: String? = null,
    val places: List<KakaoPlace> = emptyList(),
    val isPlaceSearchLoading: Boolean = false,
    val placeSearchError: String? = null,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var placeSearchJob: Job? = null
    private var routeSearchJob: Job? = null

    fun searchPlaces(query: String, isOpen: Boolean, currentLocation: GeoPoint?) {
        placeSearchJob?.cancel()
        if (!isOpen || query.trim().length < 2) {
            _uiState.update {
                it.copy(places = emptyList(), isPlaceSearchLoading = false, placeSearchError = null)
            }
            return
        }
        placeSearchJob =
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        places = emptyList(),
                        isPlaceSearchLoading = true,
                        placeSearchError = null,
                    )
                }
                delay(350)
                try {
                    val context = getApplication<Application>()
                    val result =
                        KakaoPlaceRepository.search(
                            restApiKey = context.getString(R.string.kakao_rest_api_key),
                            query = query,
                            centerLatitude = currentLocation?.latitude,
                            centerLongitude = currentLocation?.longitude,
                        )
                    _uiState.update {
                        it.copy(
                            places = result.places,
                            isPlaceSearchLoading = false,
                            placeSearchError = result.errorMessage,
                        )
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Log.e("HomeViewModel", "Place search failed", exception)
                    _uiState.update {
                        it.copy(
                            isPlaceSearchLoading = false,
                            placeSearchError = "장소 검색 중 오류가 발생했습니다.",
                        )
                    }
                }
            }
    }

    fun loadRoutes(origin: PlaceSelection, destination: PlaceSelection) {
        routeSearchJob?.cancel()
        if (!origin.hasCoordinates() || !destination.hasCoordinates()) {
            _uiState.update {
                it.copy(
                    routes = null,
                    isRouteLoading = false,
                    routeError = null,
                    routeNotice = null,
                )
            }
            return
        }
        routeSearchJob =
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        routes = null,
                        isRouteLoading = true,
                        routeError = null,
                        routeNotice = null,
                    )
                }
                try {
                    val context = getApplication<Application>()
                    val result =
                        NavigationRepository.fetchMultiRoutes(
                            context = context,
                            accessToken = context.getString(R.string.mapbox_access_token),
                            originLat = origin.latitude!!,
                            originLng = origin.longitude!!,
                            destLat = destination.latitude!!,
                            destLng = destination.longitude!!,
                        )
                    _uiState.update {
                        it.copy(
                            routes = result,
                            isRouteLoading = false,
                            routeError = result.errorMessage,
                            routeNotice = result.noticeMessage,
                        )
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Log.e("HomeViewModel", "Route search failed", exception)
                    _uiState.update {
                        it.copy(
                            isRouteLoading = false,
                            routeError = "경로 검색 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
                        )
                    }
                }
            }
    }
}
