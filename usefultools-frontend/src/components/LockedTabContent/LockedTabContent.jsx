import React from 'react';
import LockedResourceOverlay from '../LockedResourceOverlay/LockedResourceOverlay';
import styles from './LockedTabContent.module.css';

/**
 * Wrapper component for tab content that should be locked for guest users
 * Shows greyed-out content with translucent text and lock overlay
 */
const LockedTabContent = ({ isLocked, children, message = 'Please login to access this resource.' }) => {
  if (!isLocked) {
    return children;
  }

  return (
    <div className={styles.lockedContainer}>
      <div className={styles.lockedContent}>
        {children}
      </div>
      <LockedResourceOverlay message={message} />
    </div>
  );
};

export default LockedTabContent;
