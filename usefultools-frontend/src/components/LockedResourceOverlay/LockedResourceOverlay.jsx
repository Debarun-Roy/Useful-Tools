import React from 'react';
import styles from './LockedResourceOverlay.module.css';

/**
 * Overlay component shown when a resource is locked for guest users
 * Displays a lock icon and message prompting user to login
 */
const LockedResourceOverlay = ({ message = 'Please login to access this resource.' }) => {
  return (
    <div className={styles.overlay}>
      <div className={styles.content}>
        <div className={styles.lockIcon}>🔒</div>
        <p className={styles.message}>{message}</p>
      </div>
    </div>
  );
};

export default LockedResourceOverlay;
