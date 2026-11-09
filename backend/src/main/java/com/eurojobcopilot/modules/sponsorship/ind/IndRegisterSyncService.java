package com.eurojobcopilot.modules.sponsorship.ind;

import com.eurojobcopilot.modules.sponsorship.dto.IndSyncResultDto;
import com.eurojobcopilot.modules.sponsorship.dto.RegistrySyncStatusDto;

public interface IndRegisterSyncService {

    IndSyncResultDto syncIndRegister();

    RegistrySyncStatusDto getLatestSyncStatus();
}

