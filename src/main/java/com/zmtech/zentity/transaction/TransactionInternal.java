/*
 * This software is in the public domain under CC0 1.0 Universal plus a
 * Grant of Patent License.
 * 
 * To the extent possible under law, the author(s) have dedicated all
 * copyright and related and neighboring rights to this software to the
 * public domain worldwide. This software is distributed without any
 * warranty.
 * 
 * You should have received a copy of the CC0 Public Domain Dedication
 * along with this software (see the LICENSE.md file). If not, see
 * <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.zmtech.zentity.transaction;


import com.zmtech.zentity.entity.EntityContextFactory;
import com.zmtech.zentity.entity.EntityFacade;
import com.zmtech.zentity.util.MNode;

import javax.sql.DataSource;
import javax.transaction.TransactionManager;
import javax.transaction.UserTransaction;

public interface TransactionInternal {

    TransactionInternal init(EntityContextFactory ecf);
    TransactionManager getTransactionManager();
    UserTransaction getUserTransaction();
    DataSource getDataSource(EntityFacade ef, MNode datasourceNode);

    void destroy();
}
