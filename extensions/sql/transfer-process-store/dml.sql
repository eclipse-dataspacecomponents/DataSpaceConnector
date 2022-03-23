/*
 *  Copyright (c) 2020 - 2022 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Microsoft Corporation - initial API and implementation
 *
 */

-- find by id
SELECT t.*
FROM transfer_process t
WHERE t.id = 'test-id1';

-- find by drq id
SELECT t.*
FROM transfer_process t
WHERE t.id =
      (SELECT d.transfer_process_id
       FROM data_request d
       WHERE d.process_id = 'test-pid2');

-- create
INSERT INTO transfer_process (id, state, state_time_stamp, trace_context, error_detail, resource_manifest,
                              provisioned_resource_set)
VALUES ('test-id2', 400, NOW(), NULL, NULL, NULL, NULL);


INSERT INTO data_request (id, process_id, connector_address, connector_id, asset_id, contract_id, data_destination,
                          properties, transfer_type, transfer_process_id)
VALUES ('test-drq-2', 'test-pid2', 'http://anotherconnector.com', 'anotherconnector', 'asset2', 'contract2', '{}', NULL,
        default, 'test-id2');

-- delete by id
DELETE
FROM transfer_process
WHERE id = 'test-id2';


-- update
UPDATE transfer_process t
SET state = 800
WHERE id = 'test-id2';


-- next for state: select and write lease
-- select TPs with no or expired lease
SELECT *
FROM transfer_process
WHERE lease_id IS NULL
   OR lease_id IN (SELECT lease_id FROM lease WHERE (NOW > (leased_at + lease.lease_duration));

-- create lease
INSERT INTO lease (lease_id, leased_by, leased_at, lease_duration)
VALUES ('lease-1', 'yomama', NOW), DEFAULT);

-- update previously selected TPs and break lease
UPDATE transfer_process t
SET state   = 800,
    lease_id=NULL
WHERE id = 'test-id2';