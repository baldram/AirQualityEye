package pl.itrack.airqeye.store.dataclient.luftdaten.service;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import pl.itrack.airqeye.store.dataclient.luftdaten.LuftdatenClient;
import pl.itrack.airqeye.store.dataclient.luftdaten.mapper.MeasurementMapper;
import pl.itrack.airqeye.store.dataclient.luftdaten.model.LuftdatenMeasurement;
import pl.itrack.airqeye.store.measurement.entity.Measurement;
import pl.itrack.airqeye.store.measurement.enumeration.Supplier;
import pl.itrack.airqeye.store.measurement.service.MeasurementService;

@RunWith(MockitoJUnitRunner.class)
public class LuftdatenServiceTest {

  private static final LocalDateTime PAST_DATE = LocalDateTime.of(2000, 1, 1, 0, 0, 0);
  private static final int DATA_REFRESH_RANGE = 10;

  @Mock
  private LuftdatenClient luftdatenClient;

  @Mock
  private MeasurementMapper measurementMapper;

  @Mock
  private MeasurementService measurementService;

  @Captor
  private ArgumentCaptor<List<LuftdatenMeasurement>> luftdatenMeasurementsCaptor;

  @Captor
  private ArgumentCaptor<List<Measurement>> measurementsCaptor;

  @Captor
  private ArgumentCaptor<Supplier> supplierCaptor;

  @InjectMocks
  private LuftdatenService luftdatenService = new LuftdatenService();

  @Before
  public void setUp() {
    // it difficult to mock (with Mockito) the Spring's configuration property injected using @Value
    ReflectionTestUtils.setField(luftdatenService, "updateFrequencyInMinutes", DATA_REFRESH_RANGE);
  }

  @Test
  public void retrieveData() {
    // Given
    final List<LuftdatenMeasurement> luftdatenMeasurements = getRetrievedDataMock();
    final List<Measurement> convertedMeasurements = getConvertedDataMock();

    // When
    final List<Measurement> result = luftdatenService.retrieveData();

    // Then
    assertThat(luftdatenMeasurementsCaptor.getValue()).isEqualTo(luftdatenMeasurements);
    assertThat(result).isEqualTo(convertedMeasurements);
  }

  private List<LuftdatenMeasurement> getRetrievedDataMock() {
    final List<LuftdatenMeasurement> luftdatenMeasurements = singletonList(
        LuftdatenMeasurement.builder().build());
    when(luftdatenClient.retrieveData())
        .thenReturn(new ResponseEntity<>(luftdatenMeasurements, HttpStatus.OK));
    return luftdatenMeasurements;
  }

  private List<Measurement> getConvertedDataMock() {
    final List<Measurement> convertedMeasurements = singletonList(Measurement.builder().build());
    when(measurementMapper.fromDtos(luftdatenMeasurementsCaptor.capture()))
        .thenReturn(convertedMeasurements);
    return convertedMeasurements;
  }

  @Test
  public void updateIfOutdatedMeasurements() {
    // Given
    getRetrievedDataMock();
    final List<Measurement> convertedMeasurements = getConvertedDataMock();
    when(measurementService.getLatestUpdate(eq(Supplier.LUFTDATEN))).thenReturn(PAST_DATE);

    // When
    luftdatenService.refreshDataIfRequired();

    // Then
    verify(measurementService).removeData(supplierCaptor.capture());
    verify(measurementService).persist(measurementsCaptor.capture());
    assertThat(supplierCaptor.getValue()).isEqualTo(Supplier.LUFTDATEN);
    assertThat(measurementsCaptor.getValue()).hasSize(1);
    assertThat(measurementsCaptor.getValue()).isEqualTo(convertedMeasurements);
  }

  @Test
  public void noUpdateRequiredIfActualData() {
    // Given
    getRetrievedDataMock();
    getConvertedDataMock();
    // Date on border of validity, but still refresh not required.
    // This test helps to verify whether time zone is considered while calculating validity.
    final LocalDateTime dateInValidRange = LocalDateTime.now(ZoneOffset.UTC)
        .minusMinutes(DATA_REFRESH_RANGE - 1);
    when(measurementService.getLatestUpdate(eq(Supplier.LUFTDATEN))).thenReturn(dateInValidRange);

    // When
    luftdatenService.refreshDataIfRequired();

    // Then
    verify(measurementService, never()).removeData(any());
    verify(measurementService, never()).persist(any());
  }
}
