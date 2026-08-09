package com.aloneagle.tracky.di

import android.content.Context
import androidx.room.Room
import com.aloneagle.tracky.ble.protocol.GenericBleTrackerAdapter
import com.aloneagle.tracky.ble.protocol.NutFindthingAdapter
import com.aloneagle.tracky.ble.protocol.ProtocolRegistry
import com.aloneagle.tracky.ble.transport.AndroidBleConnectionManager
import com.aloneagle.tracky.ble.transport.AndroidBleScanner
import com.aloneagle.tracky.ble.transport.AndroidBluetoothDeviceCatalog
import com.aloneagle.tracky.automation.AndroidAutomationActionExecutor
import com.aloneagle.tracky.data.local.TrackyDatabase
import com.aloneagle.tracky.data.local.dao.AutomationRuleDao
import com.aloneagle.tracky.data.local.dao.BleLogEventDao
import com.aloneagle.tracky.data.local.dao.KnownTrackerDao
import com.aloneagle.tracky.data.local.dao.TrackerObservationDao
import com.aloneagle.tracky.data.repository.ExponentialProximityEstimator
import com.aloneagle.tracky.data.repository.AutomationRepositoryImpl
import com.aloneagle.tracky.data.repository.TrackerRepositoryImpl
import com.aloneagle.tracky.domain.repository.TrackerRepository
import com.aloneagle.tracky.domain.repository.AutomationRepository
import com.aloneagle.tracky.domain.service.AutomationActionExecutor
import com.aloneagle.tracky.domain.service.BleConnectionManager
import com.aloneagle.tracky.domain.service.BluetoothDeviceCatalog
import com.aloneagle.tracky.domain.service.BleLogSink
import com.aloneagle.tracky.domain.service.BleScanner
import com.aloneagle.tracky.domain.service.LocationSnapshotProvider
import com.aloneagle.tracky.domain.service.ProximityEstimator
import com.aloneagle.tracky.domain.service.ProximityRuleEvaluator
import com.aloneagle.tracky.domain.service.TrackerMonitorCoordinator
import com.aloneagle.tracky.domain.service.TrackerProtocolAdapter
import com.aloneagle.tracky.location.AndroidLocationSnapshotProvider
import com.aloneagle.tracky.logging.LogExporter
import com.aloneagle.tracky.logging.RoomBleLogSink
import com.aloneagle.tracky.logging.RoomLogExporter
import com.aloneagle.tracky.notifications.TrackyNotifications
import com.aloneagle.tracky.service.DefaultTrackerMonitorCoordinator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingModule {
    @Binds
    @Singleton
    abstract fun bindTrackerRepository(impl: TrackerRepositoryImpl): TrackerRepository

    @Binds
    @Singleton
    abstract fun bindAutomationRepository(impl: AutomationRepositoryImpl): AutomationRepository

    @Binds
    @Singleton
    abstract fun bindAutomationActionExecutor(impl: AndroidAutomationActionExecutor): AutomationActionExecutor

    @Binds
    @Singleton
    abstract fun bindBleLogSink(impl: RoomBleLogSink): BleLogSink

    @Binds
    @Singleton
    abstract fun bindBleScanner(impl: AndroidBleScanner): BleScanner

    @Binds
    @Singleton
    abstract fun bindBluetoothDeviceCatalog(impl: AndroidBluetoothDeviceCatalog): BluetoothDeviceCatalog

    @Binds
    @Singleton
    abstract fun bindBleConnectionManager(impl: AndroidBleConnectionManager): BleConnectionManager

    @Binds
    @Singleton
    abstract fun bindLocationSnapshotProvider(impl: AndroidLocationSnapshotProvider): LocationSnapshotProvider

    @Binds
    @Singleton
    abstract fun bindMonitorCoordinator(impl: DefaultTrackerMonitorCoordinator): TrackerMonitorCoordinator

    @Binds
    @Singleton
    abstract fun bindProximityEstimator(impl: ExponentialProximityEstimator): ProximityEstimator

    @Binds
    @Singleton
    abstract fun bindLogExporter(impl: RoomLogExporter): LogExporter
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TrackyDatabase = Room.databaseBuilder(
        context,
        TrackyDatabase::class.java,
        "tracky.db",
    ).build()

    @Provides
    fun provideKnownTrackerDao(database: TrackyDatabase): KnownTrackerDao = database.knownTrackerDao()

    @Provides
    fun provideObservationDao(database: TrackyDatabase): TrackerObservationDao = database.trackerObservationDao()

    @Provides
    fun provideBleLogEventDao(database: TrackyDatabase): BleLogEventDao = database.bleLogEventDao()

    @Provides
    fun provideAutomationRuleDao(database: TrackyDatabase): AutomationRuleDao = database.automationRuleDao()

    @Provides
    @Singleton
    fun provideProximityRuleEvaluator(): ProximityRuleEvaluator = ProximityRuleEvaluator()

    @Provides
    @Singleton
    fun provideProtocolRegistry(
        genericAdapter: GenericBleTrackerAdapter,
        nutAdapter: NutFindthingAdapter,
    ): ProtocolRegistry = ProtocolRegistry(
        adapters = linkedSetOf(nutAdapter, genericAdapter),
    )

    @Provides
    @Singleton
    fun provideGenericAdapter(): GenericBleTrackerAdapter = GenericBleTrackerAdapter()

    @Provides
    @Singleton
    fun provideNutAdapter(): NutFindthingAdapter = NutFindthingAdapter()

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun provideNotifications(@ApplicationContext context: Context): TrackyNotifications = TrackyNotifications(context)
}
