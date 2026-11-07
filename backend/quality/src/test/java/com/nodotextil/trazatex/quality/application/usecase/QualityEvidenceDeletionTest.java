package com.nodotextil.trazatex.quality.application.usecase;
import com.nodotextil.trazatex.quality.application.dto.Actor;
import com.nodotextil.trazatex.quality.application.dto.Role;
import com.nodotextil.trazatex.quality.application.port.out.*;
import com.nodotextil.trazatex.quality.domain.model.QualityControl;
import com.nodotextil.trazatex.quality.domain.model.EvidenceOwnerType;
import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class QualityEvidenceDeletionTest {
    final QualityControlRepositoryPort batches=mock(QualityControlRepositoryPort.class);
    final QualityFailureRepositoryPort transformations=mock(QualityFailureRepositoryPort.class);
    final EvidenceDeletionPort deletions=mock(EvidenceDeletionPort.class);
    final EvidenceStoragePort storage=mock(EvidenceStoragePort.class);
    final AuditLogPort audit=mock(AuditLogPort.class);
    final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    final UUID owner=UUID.randomUUID(), id=UUID.randomUUID();
    final Actor actor=new Actor(UUID.randomUUID(),UUID.randomUUID(),Role.COMPANY_ADMIN);
    EvidenceDeletionService service;
    @BeforeEach void prepare(){when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());service=new EvidenceDeletionService(batches,transformations,deletions,storage,audit,Clock.systemUTC(),manager);}
    @Test void foreignCompanyCannotDelete(){when(batches.findById(owner)).thenReturn(Optional.empty());assertThrows(NotFoundException.class,()->service.delete(EvidenceOwnerType.CONTROL,owner,id,actor));verifyNoInteractions(deletions,storage);}
    @Test void recordsDurableDeletionBeforeContactingStorage(){QualityControl batch=mock(QualityControl.class);when(batch.companyId()).thenReturn(actor.companyId());when(batches.findById(owner)).thenReturn(Optional.of(batch));when(deletions.markDeleted(eq(EvidenceOwnerType.CONTROL),eq(owner),eq(id),eq(actor.companyId()),any())).thenReturn(true);service.delete(EvidenceOwnerType.CONTROL,owner,id,actor);verify(manager).commit(any());verifyNoInteractions(storage);}
    /** Una evidencia que falla no frena a las demás: se aplaza con espera y la purga sigue con la siguiente. */
    @Test void aFailingEvidenceIsDeferredAndTheRestAreStillPurged(){
        UUID bad=UUID.randomUUID(), good=UUID.randomUUID();
        when(deletions.nextDeleted(any())).thenReturn(Optional.of(bad),Optional.of(good),Optional.empty());
        doThrow(new IllegalStateException("sin red")).when(storage).delete(bad.toString());
        assertEquals(1,service.purgeDeleted());
        verify(deletions).deferPurge(eq(bad),any());
        verify(deletions,never()).purge(bad);
        verify(deletions).purge(good);
    }
    @Test void aFailureOnlyDefersTheRowAndNeverPurgesItsReferences(){when(deletions.nextDeleted(any())).thenReturn(Optional.of(id),Optional.empty());doThrow(new IllegalStateException("sin red")).when(storage).delete(id.toString());assertEquals(0,service.purgeDeleted());verify(deletions).deferPurge(eq(id),any());verify(deletions,never()).purge(any());}
    /** M5: la llamada a Cloudinary no corre dentro de ninguna transacción (ni la que elige ni la que purga). */
    @Test void theStorageCallRunsOutsideAnyTransaction(){
        java.util.concurrent.atomic.AtomicInteger open=new java.util.concurrent.atomic.AtomicInteger();
        when(manager.getTransaction(any())).thenAnswer(call->{open.incrementAndGet();return new SimpleTransactionStatus();});
        doAnswer(call->{open.decrementAndGet();return null;}).when(manager).commit(any());
        doAnswer(call->{open.decrementAndGet();return null;}).when(manager).rollback(any());
        when(deletions.nextDeleted(any())).thenReturn(Optional.of(id),Optional.empty());
        java.util.List<String> seen=new java.util.ArrayList<>();
        doAnswer(call->{seen.add("delete con "+open.get()+" transacciones abiertas");return null;}).when(storage).delete(id.toString());
        doAnswer(call->{seen.add("purge con "+open.get()+" transacción abierta");return null;}).when(deletions).purge(id);
        service.purgeDeleted();
        assertEquals(java.util.List.of("delete con 0 transacciones abiertas","purge con 1 transacción abierta"),seen);
    }
    @Test void deletesStorageBeforePurgingReferences(){when(deletions.nextDeleted(any())).thenReturn(Optional.of(id),Optional.empty());assertEquals(1,service.purgeDeleted());var order=inOrder(storage,deletions);order.verify(storage).delete(id.toString());order.verify(deletions).purge(id);}
    @Test void noDeletionDoesNotContactStorage(){when(deletions.nextDeleted(any())).thenReturn(Optional.empty());assertEquals(0,service.purgeDeleted());verifyNoInteractions(storage);}
}
