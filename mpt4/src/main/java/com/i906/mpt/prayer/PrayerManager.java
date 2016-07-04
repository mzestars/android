package com.i906.mpt.prayer;

import android.location.Location;

import com.i906.mpt.api.prayer.PrayerClient;
import com.i906.mpt.api.prayer.PrayerData;
import com.i906.mpt.date.DateTimeHelper;
import com.i906.mpt.location.LocationRepository;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;
import rx.subjects.Subject;

/**
 * @author Noorzaini Ilhami
 */
@Singleton
public class PrayerManager {

    private final DateTimeHelper mDateHelper;
    private final LocationRepository mLocationRepository;
    private final PrayerClient mPrayerClient;

    private Location mLastLocation;
    private Subject<PrayerContext, PrayerContext> mPrayerStream;
    private AtomicBoolean mIsLoading = new AtomicBoolean(false);

    @Inject
    public PrayerManager(DateTimeHelper date, LocationRepository location, PrayerClient prayer) {
        mDateHelper = date;
        mLocationRepository = location;
        mPrayerClient = prayer;
    }

    public Observable<PrayerContext> getPrayerContext(final boolean refresh) {
        if (mPrayerStream == null || (refresh && !isLoading())) {
            mPrayerStream = BehaviorSubject.create();
        }

        mLocationRepository.getLocation()
                .doOnSubscribe(new Action0() {
                    @Override
                    public void call() {
                        mIsLoading.set(true);
                    }
                })
                .subscribeOn(Schedulers.io())
                .flatMap(new Func1<Location, Observable<PrayerContext>>() {
                    @Override
                    public Observable<PrayerContext> call(Location location) {
                        float distance = LocationRepository.getDistance(mLastLocation, location);
                        if (distance >= 5000 || refresh) {
                            mLastLocation = location;

                            return getCurrentPrayerTimesByCoordinate(location)
                                    .zipWith(getNextPrayerTimesByCoordinate(location), mPrayerContextCreator);
                        }

                        return Observable.empty();
                    }
                })
                .subscribe(new Action1<PrayerContext>() {
                    @Override
                    public void call(PrayerContext prayer) {
                        mIsLoading.set(false);
                        mPrayerStream.onNext(prayer);
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        mIsLoading.set(false);
                        mPrayerStream.onError(throwable);
                    }
                });

        return mPrayerStream.asObservable();
    }

    private Observable<PrayerData> getCurrentPrayerTimesByCoordinate(Location location) {
        double lat = location.getLatitude();
        double lng = location.getLongitude();

        int year = mDateHelper.getCurrentYear();
        int month = mDateHelper.getCurrentMonth();

        return mPrayerClient.getPrayerTimesByCoordinates(lat, lng, year, month);
    }

    private Observable<PrayerData> getNextPrayerTimesByCoordinate(Location location) {
        double lat = location.getLatitude();
        double lng = location.getLongitude();

        int year = mDateHelper.getCurrentYear();
        int month = mDateHelper.getNextMonth();

        if (mDateHelper.isNextMonthNewYear()) {
            year = mDateHelper.getNextYear();
        }

        return mPrayerClient.getPrayerTimesByCoordinates(lat, lng, year, month);
    }

    private final Func2<PrayerData, PrayerData, PrayerContext> mPrayerContextCreator =
            new Func2<PrayerData, PrayerData, PrayerContext>() {
                @Override
                public PrayerContext call(PrayerData current, PrayerData next) {
                    return new PrayerContextImpl(mDateHelper, current, next);
                }
            };

    public boolean isLoading() {
        return mIsLoading.get();
    }
}
